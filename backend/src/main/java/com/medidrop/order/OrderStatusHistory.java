package com.medidrop.order;

import com.medidrop.common.BaseEntity;
import com.medidrop.security.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "order_status_history", indexes = @Index(name = "idx_order_history_order", columnList = "order_id"))
@Getter
@Setter
public class OrderStatusHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 40)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 40)
    private OrderStatus toStatus;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", length = 20)
    private Role actorRole;

    @Column(length = 500)
    private String reason;
}
