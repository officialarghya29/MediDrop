package com.medidrop.pharmacy;

import com.medidrop.common.ApprovalStatus;
import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "pharmacies")
@Getter
@Setter
public class Pharmacy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false, unique = true)
    private Long ownerUserId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "license_number", nullable = false, unique = true, length = 80)
    private String licenseNumber;

    @Column(length = 20)
    private String gstin;

    @Column(name = "registered_address", nullable = false, length = 400)
    private String registeredAddress;

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

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 160)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "delivery_radius_km", nullable = false)
    private Double deliveryRadiusKm = 8.0;

    @Column(name = "min_order_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal minOrderValue = new BigDecimal("99.00");

    @Column(name = "avg_prep_minutes", nullable = false)
    private Integer avgPrepMinutes = 15;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "accepting_orders", nullable = false)
    private boolean acceptingOrders = true;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal rating = new BigDecimal("4.50");

    @Column(name = "review_note", length = 500)
    private String reviewNote;
}
