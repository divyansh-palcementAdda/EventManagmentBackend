package com.app.eventmanagmentsystem.service.interfaces;

import com.app.eventmanagmentsystem.model.AuditLog;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;

public interface AuditLogService {

    void log(String action, String entityType, String entityId,
             String eventId, AuditOutcome outcome, String requestId, String details);
}

