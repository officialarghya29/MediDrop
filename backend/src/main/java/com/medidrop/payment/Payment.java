package com.medidrop.payment;

import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments",
        indexes = @Index(name = "idx_payment_order", columnList = "order_id"),
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_provider_order", columnNames = "provider_order_id"),
                @UniqueConstraint(name = "uk_payment_client_ref", columnNames = "client_reference")
        })
@Getter
@Setter
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_order_id", nullable = false, unique = true, length = 120)
    private String providerOrderId;

    @Column(name = "provider_payment_id", length = 120)
    private String providerPaymentId;

    @Column(name = "client_reference", nullable = false, unique = true, length = 100)
    private String clientReference;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 20)
    private PaymentMethod method;

    @Column(name = "signature_verified", nullable = false)
    private boolean signatureVerified = false;

    @Column(name = "failure_reason", length = 400)
    private String failureReason;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "is_test", nullable = false)
    private boolean test = false;
}
