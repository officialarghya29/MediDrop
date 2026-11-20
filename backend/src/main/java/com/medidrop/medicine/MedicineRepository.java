package com.medidrop.medicine;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    Optional<Medicine> findByIdAndActiveTrue(Long id);

    List<Medicine> findByIdInAndActiveTrue(List<Long> ids);

    long countByCategoryId(Long categoryId);

    @Query("""
            select m from Medicine m
            where m.active = true
              and (coalesce(:q, '') = ''
                   or lower(m.name) like lower(concat('%', coalesce(:q, ''), '%'))
                   or lower(m.brand) like lower(concat('%', coalesce(:q, ''), '%'))
                   or lower(m.genericName) like lower(concat('%', coalesce(:q, ''), '%'))
                   or lower(m.strength) like lower(concat('%', coalesce(:q, ''), '%')))
              and (:categoryId is null or m.categoryId = :categoryId)
              and (:manufacturerId is null or m.manufacturerId = :manufacturerId)
              and (:pharmacyId is null or exists (
                   select i.id from PharmacyInventory i
                   where i.medicineId = m.id and i.pharmacyId = :pharmacyId
                     and i.active = true and i.stockQuantity > i.reservedQuantity))
              and (:prescriptionRequired is null or m.prescriptionRequired = :prescriptionRequired)
            """)
    Page<Medicine> search(@Param("q") String q,
                          @Param("categoryId") Long categoryId,
                          @Param("manufacturerId") Long manufacturerId,
                          @Param("pharmacyId") Long pharmacyId,
                          @Param("prescriptionRequired") Boolean prescriptionRequired,
                          Pageable pageable);

    @Query("""
            select m from Medicine m
            where m.active = true
              and m.salesBlocked = false
              and m.id <> :medicineId
              and lower(trim(m.genericName)) = lower(trim(:genericName))
              and lower(trim(m.strength)) = lower(trim(:strength))
              and lower(trim(m.dosageForm)) = lower(trim(:dosageForm))
              and lower(trim(m.packSize)) = lower(trim(:packSize))
              and m.prescriptionRequired = :prescriptionRequired
              and m.scheduleClass = :scheduleClass
            order by m.name
            """)
    List<Medicine> findSubstituteCandidates(@Param("medicineId") Long medicineId,
                                            @Param("genericName") String genericName,
                                            @Param("strength") String strength,
                                            @Param("dosageForm") String dosageForm,
                                            @Param("packSize") String packSize,
                                            @Param("prescriptionRequired") boolean prescriptionRequired,
                                            @Param("scheduleClass") String scheduleClass);

    @Query("select distinct m.name from Medicine m where m.active = true and lower(m.name) like lower(concat(coalesce(:q, ''), '%'))")
    List<String> suggest(@Param("q") String q, Pageable pageable);
}
