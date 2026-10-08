package com.example.chargeNstudy.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReviewAdminAccessTest {
    @Test
    void blankConfigurationDoesNotGrantAnyAccess() {
        ReviewAdminAccess access = new ReviewAdminAccess("");
        assertThrows(IllegalStateException.class, () -> access.requireAdmin(6860727094L));
    }

    @Test
    void configuredIdsAreTrimmedAndOtherUsersAreDenied() {
        ReviewAdminAccess access = new ReviewAdminAccess(" 6860727094, 7 ");
        assertDoesNotThrow(() -> access.requireAdmin(6860727094L));
        assertDoesNotThrow(() -> access.requireAdmin(7L));
        assertThrows(IllegalStateException.class, () -> access.requireAdmin(8L));
    }

    @Test
    void invalidIdsFailConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new ReviewAdminAccess("-1"));
        assertThrows(IllegalArgumentException.class, () -> new ReviewAdminAccess("username"));
    }
}
