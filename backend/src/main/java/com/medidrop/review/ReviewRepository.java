package com.medidrop.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByOrderId(Long orderId);

    @Query("select coalesce(avg(r.rating), 0) from Review r where r.pharmacyId = :pharmacyId")
    Double averageForPharmacy(@Param("pharmacyId") Long pharmacyId);

    long countByPharmacyId(Long pharmacyId);
}
