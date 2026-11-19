package com.medidrop.cart;

import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "cart_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cart_id", "inventory_id"}))
@Getter
@Setter
public class CartItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Column(name = "medicine_id", nullable = false)
    private Long medicineId;

    @Column(name = "pharmacy_id", nullable = false)
    private Long pharmacyId;

    @Column(nullable = false)
    private Integer quantity = 1;

    /** Display-only snapshot; pricing is always recomputed server-side at checkout. */
    @Column(name = "unit_price_snapshot", precision = 12, scale = 2)
    private BigDecimal unitPriceSnapshot;
}
