package com.medidrop.delivery;

import com.medidrop.common.ApprovalStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class DeliveryDtos {

    private DeliveryDtos() {
    }

    public record RegistrationRequest(
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$") String phone,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 400) String address,
            @NotBlank @Size(max = 40) String idProofType,
            @NotBlank @Size(max = 60) String idProofNumber,
            @Size(max = 40) String vehicleType,
            @Size(max = 30) String vehicleNumber,
            @Size(max = 120) String bankAccountName,
            @Pattern(regexp = "^\\d{0,4}$") String bankAccountLast4,
            @Size(max = 20) String payoutIfsc
    ) {
    }

    public record PartnerProfile(Long id, Long userId, String fullName, String phone, String email,
                                 String address, String vehicleType, String vehicleNumber,
                                 ApprovalStatus status, boolean online, Double latitude, Double longitude,
                                 Instant lastLocationAt, int completedDeliveries, BigDecimal rating,
                                 String reviewNote) {
    }

    public record AvailabilityRequest(@NotNull Boolean online, Double latitude, Double longitude) {
    }

    public record AssignmentRow(Long id, Long orderId, String orderNumber, String status, String pharmacyName,
                                String pharmacyAddress, Double pharmacyLatitude, Double pharmacyLongitude,
                                String customerArea, Double destinationLatitude, Double destinationLongitude,
                                Double distanceKm, Integer estimatedMinutes, int payout, Instant assignedAt,
                                boolean prescriptionRequired, boolean otpVerified, String trackingKey) {
    }

    public record LocationPing(@NotNull Double latitude, @NotNull Double longitude, Double heading,
                               Double speed, Double accuracy) {
    }

    public record CompleteRequest(@NotBlank String otp, boolean proofSubmitted) {
    }

    public record StatusRequest(@NotNull AssignmentStatus status, String note) {
    }

    public record DashboardResponse(PartnerProfile profile, boolean online, long pendingAssignments,
                                    long activeDeliveries, long completedDeliveries, BigDecimal earningsToday,
                                    BigDecimal earningsTotal, List<AssignmentRow> active) {
    }

    public record DeliveryAdminRow(Long id, String fullName, String phone, String cityArea,
                                   ApprovalStatus status, boolean online, int completedDeliveries,
                                   BigDecimal rating, Instant appliedAt, List<DocumentRow> documents) {
    }

    public record DocumentRow(Long id, String documentType, String fileName, ApprovalStatus status,
                              Instant uploadedAt) {
    }

    public record DecisionRequest(@NotNull Boolean approve, @Size(max = 400) String note) {
    }
}
