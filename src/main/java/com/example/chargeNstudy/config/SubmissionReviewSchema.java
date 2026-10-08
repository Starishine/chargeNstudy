package com.example.chargeNstudy.config;

import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Upgrades the existing PostgreSQL status check; Hibernate update does not replace old checks. */
@Component
@DependsOn("entityManagerFactory")
public class SubmissionReviewSchema implements InitializingBean {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public SubmissionReviewSchema(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void afterPropertiesSet() {
        transaction.executeWithoutResult(status -> {
            List<String> outdatedChecks = jdbc.queryForList("""
                    select c.conname from pg_constraint c
                    join pg_attribute a on a.attrelid = c.conrelid and a.attnum = any(c.conkey)
                    where c.conrelid = 'study_spot_submission'::regclass
                      and c.contype = 'c' and a.attname = 'status'
                      and (pg_get_constraintdef(c.oid) not like '%APPROVED%'
                        or pg_get_constraintdef(c.oid) not like '%REJECTED%')
                    """, String.class);
            for (String name : outdatedChecks) {
                jdbc.execute("alter table study_spot_submission drop constraint \""
                        + name.replace("\"", "\"\"") + "\"");
            }
            Integer checks = jdbc.queryForObject("""
                    select count(*) from pg_constraint c
                    join pg_attribute a on a.attrelid = c.conrelid and a.attnum = any(c.conkey)
                    where c.conrelid = 'study_spot_submission'::regclass
                      and c.contype = 'c' and a.attname = 'status'
                    """, Integer.class);
            if (checks != null && checks == 0) {
                jdbc.execute("""
                        alter table study_spot_submission add constraint study_spot_submission_status_check
                        check (status in ('DRAFT', 'PENDING', 'CANCELED', 'APPROVED', 'REJECTED'))
                        """);
            }
        });
    }
}
