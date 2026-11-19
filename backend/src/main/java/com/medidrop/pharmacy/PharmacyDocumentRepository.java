package com.medidrop.pharmacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PharmacyDocumentRepository extends JpaRepository<PharmacyDocument, Long> {

    List<PharmacyDocument> findByPharmacyId(Long pharmacyId);
}
