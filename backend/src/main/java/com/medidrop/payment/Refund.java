package com.medidrop.payment;

import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "refunds", indexes = @Index(name = "idx_refund_order", columnList = "order_id"))
@Getter
@Setter
public class Refund extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "provider_refund_id", length = 120)
    private String providerRefundId;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "processed_at")
    private Instant processedAt;
}
