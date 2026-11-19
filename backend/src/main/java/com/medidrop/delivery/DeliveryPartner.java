package com.medidrop.delivery;

import com.medidrop.common.ApprovalStatus;
import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "delivery_partners")
@Getter
@Setter
public class DeliveryPartner extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(nullable = false, length = 400)
    private String address;

    @Column(name = "id_proof_type", length = 40)
    private String idProofType;

    @Column(name = "id_proof_number", length = 60)
    private String idProofNumber;

    @Column(name = "vehicle_type", length = 40)
    private String vehicleType;

    @Column(name = "vehicle_number", length = 30)
    private String vehicleNumber;

    @Column(name = "bank_account_name", length = 120)
    private String bankAccountName;

    @Column(name = "bank_account_last4", length = 4)
    private String bankAccountLast4;

    @Column(name = "payout_ifsc", length = 20)
    private String payoutIfsc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(nullable = false)
    private boolean online = false;

    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    @Column(name = "last_location_at")
    private java.time.Instant lastLocationAt;

    @Column(name = "completed_deliveries", nullable = false)
    private Integer completedDeliveries = 0;

    @Column(name = "rating", nullable = false, precision = 3, scale = 2)
    private java.math.BigDecimal rating = new java.math.BigDecimal("5.00");

    @Column(name = "review_note", length = 500)
    private String reviewNote;
}
