package com.medidrop.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditLog> findByActorUserIdOrderByCreatedAtDesc(Long actorId, Pageable pageable);

    Page<AuditLog> findByActionIgnoreCaseOrderByCreatedAtDesc(String action, Pageable pageable);

    Page<AuditLog> findByActionIgnoreCaseAndActorUserIdOrderByCreatedAtDesc(String action, Long actorId,
                                                                            Pageable pageable);
}
