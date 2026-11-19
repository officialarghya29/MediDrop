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
              and (:q is null or lower(m.name) like lower(concat('%', :q, '%'))
                   or lower(coalesce(m.brand,'')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(m.genericName,'')) like lower(concat('%', :q, '%'))
                   or lower(coalesce(m.strength,'')) like lower(concat('%', :q, '%')))
              and (:categoryId is null or m.categoryId = :categoryId)
              and (:manufacturerId is null or m.manufacturerId = :manufacturerId)
              and (:prescriptionRequired is null or m.prescriptionRequired = :prescriptionRequired)
            """)
    Page<Medicine> search(@Param("q") String q,
                          @Param("categoryId") Long categoryId,
                          @Param("manufacturerId") Long manufacturerId,
                          @Param("prescriptionRequired") Boolean prescriptionRequired,
                          Pageable pageable);

    @Query("select distinct m.name from Medicine m where m.active = true and lower(m.name) like lower(concat(:q, '%'))")
    List<String> suggest(@Param("q") String q, Pageable pageable);
}
