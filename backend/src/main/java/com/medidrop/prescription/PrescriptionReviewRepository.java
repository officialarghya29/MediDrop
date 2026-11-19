package com.medidrop.prescription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrescriptionReviewRepository extends JpaRepository<PrescriptionReview, Long> {

    List<PrescriptionReview> findByPrescriptionIdOrderByReviewedAtDesc(Long prescriptionId);
}
