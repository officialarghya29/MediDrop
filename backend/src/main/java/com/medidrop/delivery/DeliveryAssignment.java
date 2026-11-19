package com.medidrop.delivery;

import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "delivery_assignments",
        uniqueConstraints = @UniqueConstraint(columnNames = "order_id"),
        indexes = @Index(name = "idx_assignment_partner", columnList = "partner_id"))
@Getter
@Setter
public class DeliveryAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssignmentStatus status = AssignmentStatus.ASSIGNED;

    @Column(nullable = false)
    private Integer payout = 0;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "rejection_reason", length = 400)
    private String rejectionReason;
}
