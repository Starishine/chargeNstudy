package com.example.chargeNstudy.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReviewAdminAccess {
    private final Set<Long> adminIds;

    public ReviewAdminAccess(@Value("${telegram.admin-user-ids:}") String configuredIds) {
        adminIds = Arrays.stream(configuredIds.split(","))
                .map(String::trim).filter(id -> !id.isEmpty())
                .map(Long::valueOf).collect(Collectors.toUnmodifiableSet());
        if (adminIds.stream().anyMatch(id -> id <= 0)) {
            throw new IllegalArgumentException("TELEGRAM_ADMIN_USER_IDS must contain positive Telegram user IDs.");
        }
    }

    public void requireAdmin(long userId) {
        if (!adminIds.contains(userId)) {
            throw new IllegalStateException("Review is available to configured admins only.");
        }
    }
}
