package com.medidrop.delivery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, Long> {

    Optional<DeliveryAssignment> findByOrderId(Long orderId);

    Optional<DeliveryAssignment> findByIdAndPartnerId(Long id, Long partnerId);

    Page<DeliveryAssignment> findByPartnerIdOrderByCreatedAtDesc(Long partnerId, Pageable pageable);

    List<DeliveryAssignment> findByPartnerIdAndStatusIn(Long partnerId, Collection<AssignmentStatus> statuses);

    long countByPartnerIdAndStatusIn(Long partnerId, Collection<AssignmentStatus> statuses);

    long countByPartnerIdAndStatus(Long partnerId, AssignmentStatus status);
}
