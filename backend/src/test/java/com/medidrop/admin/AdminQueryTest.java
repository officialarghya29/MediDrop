package com.medidrop.admin;

import com.medidrop.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invalid query parameters must be client errors, not unhandled 500s. These paths
 * previously called Enum.valueOf directly and leaked IllegalArgumentException.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminQueryTest {

    @Autowired
    private AdminService adminService;

    @Test
    void invalidApprovalFiltersReturnBadRequest() {
        assertThatThrownBy(() -> adminService.pharmacies("NOT_A_STATUS", null, 0, 20))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unknown approval status");

        assertThatThrownBy(() -> adminService.partners("NOT_A_STATUS", 0, 20))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unknown approval status");
    }
}
