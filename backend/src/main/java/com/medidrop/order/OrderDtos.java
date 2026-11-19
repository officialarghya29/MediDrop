package com.medidrop.order;

import com.medidrop.payment.PaymentMethod;
import com.medidrop.security.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record PlaceOrderRequest(
            @NotNull Long addressId,
            Long prescriptionId,
            @Size(max = 40) String couponCode,
            @NotNull PaymentMethod paymentMethod,
            @Size(max = 500) String note
    ) {
    }

    public record Item(Long id, Long medicineId, String medicineName, BigDecimal unitPrice, int quantity,
                       BigDecimal lineTotal, boolean prescriptionRequired) {
    }

    public record HistoryEntry(String fromStatus, String toStatus, String actorRole, String reason,
                               Instant at) {
    }

    public record PartnerInfo(Long id, String name, String phone, Double latitude, Double longitude,
                              Instant lastLocationAt, String vehicleType) {
    }

    public record OrderSummary(Long id, String orderNumber, String status, String pharmacyName,
                               BigDecimal totalAmount, int itemCount, Instant placedAt, String paymentMethod,
                               boolean prescriptionRequired, String city, String imageHint) {
    }

    public record OrderDetail(Long id, String orderNumber, String status, Long pharmacyId, String pharmacyName,
                              String pharmacyPhone, String customerName, String customerPhone,
                              String addressLine, String city, String state, String pincode,
                              Double latitude, Double longitude, Double pharmacyLatitude,
                              Double pharmacyLongitude, BigDecimal subtotal, BigDecimal discount,
                              BigDecimal deliveryFee, BigDecimal taxAmount, BigDecimal totalAmount,
                              String paymentMethod, boolean prescriptionRequired, Long prescriptionId,
                              String prescriptionStatus, String couponCode, String customerNote,
                              String cancellationReason, Instant placedAt, Instant deliveredAt,
                              Double distanceKm, Integer etaMinutes, List<Item> items,
                              List<HistoryEntry> history, PartnerInfo deliveryPartner,
                              String trackingKey, String deliveryOtp, boolean deliveryOtpVerified) {
    }

    public record CancelRequest(@Size(min = 3, max = 500) String reason) {
    }

    public record TransitionRequest(@NotNull OrderStatus status, @Size(max = 500) String reason) {
    }

    public record InvoiceResponse(String orderNumber, Instant issuedAt, String sellerName, String sellerAddress,
                                  String sellerGstin, String sellerLicense, String buyerName, String buyerAddress,
                                  List<Item> items, BigDecimal subtotal, BigDecimal discount, BigDecimal taxAmount,
                                  BigDecimal deliveryFee, BigDecimal totalAmount, String paymentMethod,
                                  String paymentReference, String note) {
    }

    public record ReviewRequest(@NotNull Integer rating, @Size(max = 1000) String comment) {
    }

    public record TrackingResponse(String orderNumber, String status, String trackingKey, Instant updatedAt,
                                   PartnerInfo partner, Double partnerLatitude, Double partnerLongitude,
                                   Instant partnerUpdatedAt, Integer etaMinutes, String destinationLabel,
                                   Double destinationLatitude, Double destinationLongitude,
                                   Double pharmacyLatitude, Double pharmacyLongitude) {
    }

    public record StatusCounts(long total, long active, long delivered, long cancelled) {
    }

    public record AdminOrderRow(Long id, String orderNumber, String customerName, String pharmacyName,
                                String status, BigDecimal totalAmount, Instant placedAt, Role customerRole) {
    }
}
