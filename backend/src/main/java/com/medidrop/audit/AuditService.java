package com.medidrop.audit;

import com.medidrop.security.CurrentUser;
import com.medidrop.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository repository;

    public void record(String action, String entityType, Object entityId, String details) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId == null ? null : String.valueOf(entityId));
        entry.setDetails(details);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            entry.setActorUserId(principal.getUserId());
            entry.setActorRole(principal.getRole().name());
        }
        entry.setIpAddress(null);
        repository.save(entry);
        log.info("audit action={} entity={}:{} actor={}", action, entityType, entityId, entry.getActorUserId());
    }

    public void record(String action, String entityType, Object entityId) {
        record(action, entityType, entityId, null);
    }

    public void recordForUser(Long actorId, String actorRole, String action, String entityType, Object entityId,
                              String details) {
        AuditLog entry = new AuditLog();
        entry.setActorUserId(actorId);
        entry.setActorRole(actorRole);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId == null ? null : String.valueOf(entityId));
        entry.setDetails(details);
        repository.save(entry);
    }
}
