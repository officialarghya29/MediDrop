package com.medidrop.admin;

import com.medidrop.common.ApprovalStatus;
import com.medidrop.security.Role;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record Dashboard(long totalCustomers, long totalPharmacies, long pendingPharmacies,
                            long totalPartners, long pendingPartners, long totalOrders, long activeOrders,
                            long completedOrders, long cancelledOrders, BigDecimal revenue,
                            BigDecimal refunded, long ordersToday, long pendingPrescriptions,
                            long openSupportTickets, List<TrendPoint> orderTrend) {
    }

    public record TrendPoint(String date, long orders, BigDecimal revenue) {
    }

    public record UserRow(Long id, String fullName, String email, String phone, Role role,
                          boolean enabled, boolean locked, boolean emailVerified, Instant joinedAt) {
    }

    public record UserDetail(UserRow user, long orders, Instant lastLoginAt, Instant deletionRequestedAt) {
    }

    public record SuspendRequest(@NotNull Boolean suspend, @Size(max = 400) String reason) {
    }

    public record PharmacyRow(Long id, String name, String city, String licenseNumber, String phone,
                              ApprovalStatus status, Instant registeredAt, BigDecimal rating,
                              List<AdminMisc.DocumentRow> documents) {
    }

    public record PartnerRow(Long id, String fullName, String phone, String vehicleType, ApprovalStatus status,
                             boolean online, int completedDeliveries, BigDecimal rating, Instant appliedAt,
                             List<AdminMisc.DocumentRow> documents) {
    }

    public record CategoryRequest(@NotBlank @Size(max = 80) String name, @Size(max = 300) String description,
                                  boolean prescriptionOnly, Integer displayOrder, Boolean active) {
    }

    public record CouponRequest(@NotBlank @Size(max = 40) String code, @NotNull String type,
                                @NotNull @DecimalMin("0.01") BigDecimal value,
                                @DecimalMin("0.00") BigDecimal minOrderValue,
                                @DecimalMin("0.00") BigDecimal maxDiscount,
                                Boolean active, Integer usageLimit, String description) {
    }

    public record SettingRequest(@NotBlank String value, @Size(max = 300) String description, Boolean publicSetting) {
    }

    public record CouponRow(Long id, String code, String type, BigDecimal value, BigDecimal minOrderValue,
                            BigDecimal maxDiscount, boolean active, Integer usageLimit, int usedCount,
                            Instant validTo) {
    }

    public record RefundRow(Long id, Long orderId, BigDecimal amount, String status, String reason,
                            Instant processedAt) {
    }

    public record AuditRow(Long id, String actorRole, Long actorUserId, String action, String entityType,
                           String entityId, String details, Instant at) {
    }

    public record PlatformReport(long days, List<TrendPoint> orders, BigDecimal grossRevenue,
                                 BigDecimal averageOrderValue, double cancellationRate,
                                 double averageDeliveryMinutes, long deliveredOrders,
                                 List<NamedCount> topPharmacies, List<NamedCount> topMedicines) {
    }

    public record NamedCount(String label, long count) {
    }
}
