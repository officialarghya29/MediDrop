package com.medidrop.pharmacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PharmacyOperatingHourRepository extends JpaRepository<PharmacyOperatingHour, Long> {

    List<PharmacyOperatingHour> findByPharmacyIdOrderByDayOfWeek(Long pharmacyId);

    void deleteByPharmacyId(Long pharmacyId);
}
