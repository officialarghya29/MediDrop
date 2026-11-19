package com.medidrop.delivery;

import com.medidrop.common.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeliveryPartnerRepository extends JpaRepository<DeliveryPartner, Long> {

    Optional<DeliveryPartner> findByUserId(Long userId);

    long countByStatus(ApprovalStatus status);

    Page<DeliveryPartner> findByStatus(ApprovalStatus status, Pageable pageable);

    @Query("select p from DeliveryPartner p where p.status = com.medidrop.common.ApprovalStatus.APPROVED and p.online = true")
    List<DeliveryPartner> findOnlineApproved();

    @Query("""
            select p from DeliveryPartner p
            where p.status = com.medidrop.common.ApprovalStatus.APPROVED and p.online = true
              and p.currentLatitude between :minLat and :maxLat
              and p.currentLongitude between :minLng and :maxLng
            """)
    List<DeliveryPartner> findOnlineInBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
                                          @Param("minLng") double minLng, @Param("maxLng") double maxLng);
}
