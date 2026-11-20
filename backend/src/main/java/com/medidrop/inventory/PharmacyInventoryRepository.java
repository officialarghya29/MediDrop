package com.medidrop.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PharmacyInventoryRepository extends JpaRepository<PharmacyInventory, Long> {

    List<PharmacyInventory> findByPharmacyIdOrderByUpdatedAtDesc(Long pharmacyId);

    Optional<PharmacyInventory> findByPharmacyIdAndMedicineId(Long pharmacyId, Long medicineId);

    Optional<PharmacyInventory> findByIdAndPharmacyId(Long id, Long pharmacyId);

    Optional<InventorySnapshot> findSnapshotById(Long id);

    interface InventorySnapshot {
        Long getId();
        Long getMedicineId();
        java.math.BigDecimal getPrice();
        Integer getStockQuantity();
        Integer getReservedQuantity();
        boolean isActive();

        default int availableQuantity() {
            return Math.max(0, getStockQuantity() - getReservedQuantity());
        }
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PharmacyInventory i where i.id = :id")
    Optional<PharmacyInventory> findForUpdate(@Param("id") Long id);

    @Query("""
            select i from PharmacyInventory i
            where i.medicineId = :medicineId and i.active = true and i.stockQuantity > i.reservedQuantity
              and i.pharmacyId in :pharmacyIds
            """)
    List<PharmacyInventory> findAvailableForPharmacies(@Param("medicineId") Long medicineId,
                                                       @Param("pharmacyIds") List<Long> pharmacyIds);

    @Query("select i from PharmacyInventory i where i.pharmacyId = :pharmacyId and i.stockQuantity <= i.lowStockThreshold and i.active = true")
    List<PharmacyInventory> findLowStock(@Param("pharmacyId") Long pharmacyId);

    @Query("select count(i) from PharmacyInventory i where i.pharmacyId = :pharmacyId and i.stockQuantity <= i.lowStockThreshold and i.active = true")
    long countLowStock(@Param("pharmacyId") Long pharmacyId);

    @Query("""
            select i.medicineId, min(i.price), max(i.mrp), sum(i.stockQuantity), count(i)
            from PharmacyInventory i
            where i.medicineId in :medicineIds and i.active = true and i.stockQuantity > 0
            group by i.medicineId
            """)
    List<Object[]> aggregateForMedicines(@Param("medicineIds") List<Long> medicineIds);

    @Query("""
            select i from PharmacyInventory i
            where i.medicineId = :medicineId and i.active = true and i.stockQuantity > 0
            order by i.price asc
            """)
    List<PharmacyInventory> findAvailableByMedicine(@Param("medicineId") Long medicineId);

    List<PharmacyInventory> findByMedicineIdInAndActiveTrue(List<Long> medicineIds);

    long countByPharmacyIdAndActiveTrue(Long pharmacyId);
}
