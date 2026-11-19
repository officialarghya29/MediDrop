package com.medidrop.delivery;

import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "delivery_locations", indexes = @Index(name = "idx_delivery_location_assignment", columnList = "assignment_id"))
@Getter
@Setter
public class DeliveryLocation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    private Double heading;

    private Double speed;

    private Double accuracy;
}
