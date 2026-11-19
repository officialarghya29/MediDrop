package com.medidrop.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            select a from AuditLog a
            where (:action is null or lower(a.action) = lower(:action))
              and (:actorId is null or a.actorUserId = :actorId)
            order by a.createdAt desc
            """)
    Page<AuditLog> search(@Param("action") String action, @Param("actorId") Long actorId, Pageable pageable);
}
