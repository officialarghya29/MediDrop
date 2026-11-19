package com.medidrop.pharmacy;

import com.medidrop.common.ApprovalStatus;
import jakarta.validation.constraints.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public final class PharmacyDtos {

    private PharmacyDtos() {
    }

    public record NearbyPharmacy(Long id, String name, String address, String city, String pincode,
                                 Double latitude, Double longitude, double distanceKm, double deliveryRadiusKm,
                                 BigDecimal minOrderValue, BigDecimal rating, boolean acceptingOrders,
                                 boolean withinDeliveryArea, int estimatedPrepMinutes) {
    }

    public record PharmacyDetail(Long id, String name, String address, String city, String state, String pincode,
                                 Double latitude, Double longitude, BigDecimal rating, BigDecimal minOrderValue,
                                 double deliveryRadiusKm, String phone, List<HourResponse> hours) {
    }

    public record HourResponse(int dayOfWeek, LocalTime openTime, LocalTime closeTime, boolean closed) {
    }

    public record RegistrationRequest(
            @NotBlank @Size(max = 160) String pharmacyName,
            @NotBlank @Size(max = 120) String ownerName,
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$") String phone,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 80) String licenseNumber,
            @Size(max = 20) String gstin,
            @NotBlank @Size(max = 400) String registeredAddress,
            @NotBlank String city,
            @NotBlank String state,
            @NotBlank @Pattern(regexp = "^\\d{6}$") String pincode,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @DecimalMin("1.0") @DecimalMax("50.0") Double deliveryRadiusKm
    ) {
    }

    public record DocumentResponse(Long id, String documentType, String fileName, ApprovalStatus status,
                                   Instant uploadedAt, String remarks) {
    }

    public record DashboardResponse(long totalOrders, long pendingOrders, long awaitingPrescription,
                                    long readyForPickup, long completedOrders, long cancelledOrders,
                                    BigDecimal revenueLast30Days, long lowStockItems, long activeListings,
                                    long pendingPrescriptionReviews, String pharmacyName, ApprovalStatus status,
                                    boolean acceptingOrders, BigDecimal rating) {
    }

    public record StatusUpdateRequest(@NotNull Boolean acceptingOrders) {
    }

    public record OpeningHoursRequest(@NotNull List<HourRequest> hours) {
    }

    public record HourRequest(@Min(1) @Max(7) int dayOfWeek, @NotNull LocalTime openTime,
                              @NotNull LocalTime closeTime, boolean closed) {
    }

    public record AdminDecisionRequest(@NotNull Boolean approve, @Size(max = 400) String note) {
    }

    public record PharmacyAdminView(Long id, String name, String city, String phone, String email,
                                    String licenseNumber, ApprovalStatus status, Instant registeredAt,
                                    long orders, List<DocumentResponse> documents) {
    }

    public record UploadedDocument(MultipartFile file, String documentType) {
    }
}
