package com.example.chargeNstudy.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubmissionReviewSchemaTest {
    @Test
    void replacesOldStatusCheckInOneTransaction() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of("old_status_check"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        new SubmissionReviewSchema(jdbc, manager).afterPropertiesSet();
        verify(jdbc).execute("alter table study_spot_submission drop constraint \"old_status_check\"");
        verify(jdbc).execute(argThat((String sql) -> sql.contains("'APPROVED', 'REJECTED'")));
        verify(manager).commit(any());
    }

    @Test
    void currentStatusCheckNeedsNoDdl() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        new SubmissionReviewSchema(jdbc, manager).afterPropertiesSet();
        verify(jdbc, never()).execute(anyString());
    }
}
