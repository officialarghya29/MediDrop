package com.medidrop.inventory;

import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "inventory_transactions",
        indexes = @Index(name = "idx_inventory_tx_inventory", columnList = "inventory_id"))
@Getter
@Setter
public class InventoryTransaction extends BaseEntity {

    public enum Type { RESTOCK, SALE, ADJUSTMENT, RETURN, EXPIRY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(name = "quantity_delta", nullable = false)
    private Integer quantityDelta;

    @Column(nullable = false)
    private Integer balanceAfter;

    @Column(length = 300)
    private String reason;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "order_id")
    private Long orderId;
}
