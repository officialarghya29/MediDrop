package com.medidrop.inventory;

import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "inventory_batches",
        uniqueConstraints = @UniqueConstraint(columnNames = {"inventory_id", "batch_number"}))
@Getter
@Setter
public class InventoryBatch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Column(name = "batch_number", nullable = false, length = 60)
    private String batchNumber;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false)
    private Integer quantity = 0;

    @Column(name = "cost_price", precision = 12, scale = 2)
    private java.math.BigDecimal costPrice;
}
