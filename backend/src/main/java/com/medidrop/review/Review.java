package com.medidrop.review;

import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(columnNames = "order_id"))
@Getter
@Setter
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "pharmacy_id", nullable = false)
    private Long pharmacyId;

    @Column(nullable = false)
    private Integer rating;

    @Column(length = 1000)
    private String comment;
}
