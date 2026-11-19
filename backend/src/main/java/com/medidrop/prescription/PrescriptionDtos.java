package com.medidrop.prescription;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class PrescriptionDtos {

    private PrescriptionDtos() {
    }

    public record PrescriptionResponse(Long id, String fileName, String contentType, Long sizeBytes,
                                       String status, Long pharmacyId, Long orderId, String notes,
                                       String rejectionReason, Instant uploadedAt, Instant reviewedAt,
                                       String fileUrl, String ocrExtractedText, boolean demo) {
    }

    public record ReviewDecisionRequest(@NotNull Boolean approve,
                                        @Size(max = 500) String comments,
                                        @Size(max = 2000) String verifiedItems) {
    }

    public record PendingReview(Long id, Long customerId, String customerName, String fileName,
                                Instant uploadedAt, String status, String notes, Long orderId,
                                String orderNumber) {
    }

    public record ReviewHistory(Long id, String decision, String comments, Instant reviewedAt) {
    }

    public record CustomerPrescriptionDetail(PrescriptionResponse prescription, List<ReviewHistory> reviews) {
    }
}
