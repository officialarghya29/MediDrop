package com.medidrop.admin;

import com.medidrop.common.ApprovalStatus;

import java.time.Instant;

public final class AdminMisc {

    private AdminMisc() {
    }

    public record DocumentRow(Long id, String documentType, String fileName, ApprovalStatus status,
                              Instant uploadedAt) {
    }
}
