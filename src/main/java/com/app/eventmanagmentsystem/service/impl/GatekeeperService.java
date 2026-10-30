package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.DuplicateResourceException;
import com.app.eventmanagmentsystem.exceptions.EventAccessDeniedException;
import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventGatekeeper;
import com.app.eventmanagmentsystem.model.User;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.repository.EventGatekeeperRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.repository.UserRepository;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatekeeperService {

    private final EventGatekeeperRepository gatekeeperRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @PreAuthorize("hasAuthority('GATEKEEPER_ASSIGN')")
    @Transactional
    public void assignGatekeeper(UUID eventId, UUID userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        boolean hasGatekeeperRole = user.getRoles().stream()
                .anyMatch(r -> "GATEKEEPER".equals(r.getName()));
        if (!hasGatekeeperRole) {
            throw new InvalidRequestException("User does not have the GATEKEEPER role.");
        }

        if (gatekeeperRepository.existsActiveAssignment(eventId, userId)) {
            throw new DuplicateResourceException("User is already an active gatekeeper for this event.");
        }

        AppUserDetails actor = currentUser();

        EventGatekeeper assignment = new EventGatekeeper();
        assignment.setEvent(event);
        assignment.setUser(user);
        assignment.setAssignedAt(Instant.now());
        assignment.setAssignedById(actor.getUserId().toString());
        assignment.setAssignedByName(actor.getFullName());
        assignment.setActive(true);

        gatekeeperRepository.save(assignment);
        auditLogService.log("GATEKEEPER_ASSIGN", "EventGatekeeper", null,
                eventId.toString(), AuditOutcome.SUCCESS, null,
                "Gatekeeper assigned: " + userId);
    }

    @PreAuthorize("hasAuthority('GATEKEEPER_REMOVE')")
    @Transactional
    public void removeGatekeeper(UUID eventId, UUID userId) {
        EventGatekeeper assignment = gatekeeperRepository.findActiveAssignment(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Active gatekeeper assignment",
                        "event=" + eventId + ",user=" + userId));

        AppUserDetails actor = currentUser();
        assignment.setActive(false);
        assignment.setDeactivatedAt(Instant.now());
        assignment.setDeactivatedById(actor.getUserId().toString());
        gatekeeperRepository.save(assignment);

        auditLogService.log("GATEKEEPER_REMOVE", "EventGatekeeper", assignment.getId().toString(),
                eventId.toString(), AuditOutcome.SUCCESS, null,
                "Gatekeeper removed: " + userId);
    }

    private AppUserDetails currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserDetails ud) {
            return ud;
        }
        throw new EventAccessDeniedException();
    }
}

