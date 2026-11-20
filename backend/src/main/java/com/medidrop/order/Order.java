package com.medidrop.order;

import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders",
        indexes = {
                @Index(name = "idx_order_customer", columnList = "customer_id"),
                @Index(name = "idx_order_pharmacy", columnList = "pharmacy_id"),
                @Index(name = "idx_order_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_order_number", columnNames = "order_number"),
                @UniqueConstraint(name = "uk_order_idempotency", columnNames = "idempotency_key")
        })
@Getter
@Setter
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 30)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "pharmacy_id", nullable = false)
    private Long pharmacyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(nullable = false, length = 200)
    private String line1;

    @Column(length = 200)
    private String line2;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 80)
    private String state;

    @Column(nullable = false, length = 10)
    private String pincode;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod = "COD";

    @Column(name = "prescription_id")
    private Long prescriptionId;

    @Column(name = "prescription_required", nullable = false)
    private boolean prescriptionRequired = false;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Column(name = "delivery_otp_hash", length = 128)
    private String deliveryOtpHash;

    @Column(name = "delivery_otp_verified", nullable = false)
    private boolean deliveryOtpVerified = false;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "customer_note", length = 500)
    private String customerNote;

    @Column(name = "tracking_key", unique = true, length = 40)
    private String trackingKey;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt = Instant.now();

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancel_eligible_until")
    private Instant cancelEligibleUntil;

    @Column(name = "stock_restored", nullable = false)
    private boolean stockRestored = false;

    @Column(name = "coupon_released", nullable = false)
    private boolean couponReleased = false;

    public boolean isTerminal() {
        return status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED
                || status == OrderStatus.REFUNDED || status == OrderStatus.FAILED;
    }
}
