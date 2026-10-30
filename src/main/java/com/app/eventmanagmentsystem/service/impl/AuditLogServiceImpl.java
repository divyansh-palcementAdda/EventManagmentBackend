package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.model.AuditLog;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.repository.AuditLogRepository;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entityType, String entityId,
                    String eventId, AuditOutcome outcome, String requestId, String details) {
        try {
            String actorId = "SYSTEM";
            String actorName = "System";
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AppUserDetails ud) {
                actorId = ud.getUserId().toString();
                actorName = ud.getFullName();
            }

            AuditLog entry = AuditLog.builder()
                    .actorId(actorId)
                    .actorName(actorName)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .eventId(eventId)
                    .outcome(outcome)
                    .requestId(requestId)
                    .details(details)
                    .build();

            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Audit failures must never break the primary operation
            log.error("Failed to write audit log for action [{}]: {}", action, e.getMessage());
        }
    }
}

