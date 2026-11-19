package com.medidrop.pharmacy;

import com.medidrop.common.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {

    Optional<Pharmacy> findByOwnerUserId(Long ownerUserId);

    boolean existsByLicenseNumberIgnoreCase(String licenseNumber);

    List<Pharmacy> findByStatus(ApprovalStatus status);

    long countByStatus(ApprovalStatus status);

    @Query("""
            select p from Pharmacy p
            where p.status = com.medidrop.common.ApprovalStatus.APPROVED
              and p.active = true
              and p.acceptingOrders = true
              and p.latitude between :minLat and :maxLat
              and p.longitude between :minLng and :maxLng
            """)
    List<Pharmacy> findCandidatesInBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
                                       @Param("minLng") double minLng, @Param("maxLng") double maxLng);

    @Query("select p from Pharmacy p where p.status = :status and (:q is null or lower(p.name) like lower(concat('%', :q, '%')))")
    Page<Pharmacy> searchByStatus(@Param("status") ApprovalStatus status, @Param("q") String q, Pageable pageable);

    @Query("""
            select p from Pharmacy p
            where p.status = com.medidrop.common.ApprovalStatus.APPROVED and p.active = true
              and (:q is null or lower(p.name) like lower(concat('%', :q, '%')))
            """)
    Page<Pharmacy> searchApproved(@Param("q") String q, Pageable pageable);
}
