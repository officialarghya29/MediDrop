package com.medidrop.medicine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicineCategoryRepository extends JpaRepository<MedicineCategory, Long> {

    List<MedicineCategory> findByActiveTrueOrderByDisplayOrderAsc();

    Optional<MedicineCategory> findBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);
}
