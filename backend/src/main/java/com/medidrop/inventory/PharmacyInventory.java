package com.medidrop.inventory;

import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "pharmacy_inventory",
        uniqueConstraints = @UniqueConstraint(columnNames = {"pharmacy_id", "medicine_id"}),
        indexes = @Index(name = "idx_inventory_medicine", columnList = "medicine_id"))
@Getter
@Setter
public class PharmacyInventory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pharmacy_id", nullable = false)
    private Long pharmacyId;

    @Column(name = "medicine_id", nullable = false)
    private Long medicineId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal mrp;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity = 0;

    @Column(name = "low_stock_threshold", nullable = false)
    private Integer lowStockThreshold = 10;

    @Column(nullable = false)
    private boolean active = true;

    /** Left null on new rows so Spring Data treats them as new (persist, not merge). */
    @Version
    @Column(nullable = false)
    private Long version;

    public int availableQuantity() {
        return Math.max(0, stockQuantity - reservedQuantity);
    }
}
