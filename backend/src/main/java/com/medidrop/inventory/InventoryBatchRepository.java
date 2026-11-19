package com.medidrop.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {

    List<InventoryBatch> findByInventoryIdOrderByExpiryDateAsc(Long inventoryId);

    @Query("""
            select b from InventoryBatch b join PharmacyInventory i on i.id = b.inventoryId
            where i.pharmacyId = :pharmacyId and b.expiryDate <= :cutoff and b.quantity > 0
            order by b.expiryDate asc
            """)
    List<InventoryBatch> findExpiringForPharmacy(@Param("pharmacyId") Long pharmacyId,
                                                 @Param("cutoff") LocalDate cutoff);
}
