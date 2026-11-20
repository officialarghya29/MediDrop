package com.medidrop.prescription;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    List<Prescription> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<Prescription> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Prescription> findByIdAndPharmacyId(Long id, Long pharmacyId);

    Page<Prescription> findByPharmacyIdAndStatus(Long pharmacyId, PrescriptionStatus status, Pageable pageable);

    Page<Prescription> findByStatus(PrescriptionStatus status, Pageable pageable);

    long countByStatus(PrescriptionStatus status);
}
