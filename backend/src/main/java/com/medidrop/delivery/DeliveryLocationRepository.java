package com.medidrop.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryLocationRepository extends JpaRepository<DeliveryLocation, Long> {

    Optional<DeliveryLocation> findTopByAssignmentIdOrderByCreatedAtDesc(Long assignmentId);
}
