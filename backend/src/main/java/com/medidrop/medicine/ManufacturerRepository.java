package com.medidrop.medicine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManufacturerRepository extends JpaRepository<Manufacturer, Long> {

    List<Manufacturer> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
