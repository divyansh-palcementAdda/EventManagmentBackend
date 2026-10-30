package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.*;
import com.app.eventmanagmentsystem.model.*;
import com.app.eventmanagmentsystem.model.enums.*;
import com.app.eventmanagmentsystem.payload.pass.ActivatePassRequest;
import com.app.eventmanagmentsystem.payload.pass.PassResponse;
import com.app.eventmanagmentsystem.repository.*;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import com.app.eventmanagmentsystem.util.QrTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles transactional pass activation.
 *
 * <p>Within a single transaction:
 * <ol>
 *   <li>Lock the pass row (PESSIMISTIC_WRITE) to prevent concurrent activation.</li>
 *   <li>Validate the token hash.</li>
 *   <li>Validate pass state (must be NOT_ACTIVATED).</li>
 *   <li>Create the Attendee record.</li>
 *   <li>Associate the attendee with the pass.</li>
 *   <li>Update pass status to ACTIVATED.</li>
 *   <li>Persist an EmailOutbox record in the same TX.</li>
 * </ol>
 *
 * <p>If any step fails the entire transaction rolls back.
 * Email delivery failures do not undo activation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PassActivationService {

    private final EventPassRepository passRepository;
    private final AttendeeRepository attendeeRepository;
    private final EventRepository eventRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final QrTokenUtil qrTokenUtil;
    private final AuditLogService auditLogService;

    @Transactional
    public PassResponse activatePass(ActivatePassRequest request) {
        String tokenHash = qrTokenUtil.hashToken(request.getRawToken());

        // Lock the pass row before any state checks
        EventPass pass = passRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(InvalidQrTokenException::new);

        // Validate pass belongs to requested event
        if (!pass.getEvent().getId().equals(request.getEventId())) {
            throw new InvalidQrTokenException();
        }

        // State validation
        if (pass.getStatus() == PassStatus.REVOKED) {
            throw new PassRevokedException();
        }
        if (pass.getStatus() == PassStatus.ACTIVATED) {
            throw new PassAlreadyActivatedException();
        }

        // Activation window check
        Event event = pass.getEvent();
        Instant now = Instant.now();
        if (event.getActivationStartsAt() != null && now.isBefore(event.getActivationStartsAt())) {
            throw new InvalidRequestException("Pass activation window has not opened yet.");
        }
        if (event.getActivationEndsAt() != null && now.isAfter(event.getActivationEndsAt())) {
            throw new InvalidRequestException("Pass activation window has closed.");
        }

        // Prevent duplicate attendee-email per event
        String normalizedEmail = request.getAttendeeEmail().toLowerCase().trim();
        if (attendeeRepository.existsByEventAndEmail(event.getId(), normalizedEmail)) {
            throw new DuplicateResourceException(
                    "An attendee with this email is already registered for this event.");
        }

        // Create attendee
        Attendee attendee = new Attendee();
        attendee.setEvent(event);
        attendee.setFullName(request.getAttendeeFullName());
        attendee.setEmail(normalizedEmail);
        attendee.setMobileNumber(request.getAttendeeMobileNumber());
        attendee.setOrganization(request.getAttendeeOrganization());
        attendee.setDesignation(request.getAttendeeDesignation());
        attendee.setRegistrationStatus(RegistrationStatus.CONFIRMED);
        attendee.setRegisteredAt(now);
        Attendee savedAttendee = attendeeRepository.save(attendee);

        // Activate pass
        pass.setAttendee(savedAttendee);
        pass.setStatus(PassStatus.ACTIVATED);
        pass.setActivatedAt(now);
        EventPass savedPass = passRepository.save(pass);

        // Enqueue activation email (in same transaction — transactional outbox pattern)
        enqueueActivationEmail(savedPass, savedAttendee);

        auditLogService.log("PASS_ACTIVATE", "EventPass", pass.getId().toString(),
                event.getId().toString(), AuditOutcome.SUCCESS, null,
                "Pass activated by attendee: " + normalizedEmail);

        return PassResponse.builder()
                .id(savedPass.getId())
                .passNumber(savedPass.getPassNumber())
                .status(savedPass.getStatus())
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .generatedAt(savedPass.getGeneratedAt())
                .activatedAt(savedPass.getActivatedAt())
                .rawToken(null) // never returned after activation
                .build();
    }

    @PreAuthorize("hasAuthority('PASS_REVOKE')")
    @Transactional
    public void revokePass(UUID passId, String reason, String revokedById, String revokedByName) {
        EventPass pass = passRepository.findByIdForUpdate(passId)
                .orElseThrow(() -> new ResourceNotFoundException("EventPass", passId.toString()));

        if (pass.getStatus() == PassStatus.REVOKED) {
            throw new InvalidRequestException("Pass is already revoked.");
        }

        pass.setStatus(PassStatus.REVOKED);
        pass.setRevokedAt(Instant.now());
        pass.setRevokedById(revokedById);
        pass.setRevokedByName(revokedByName);
        pass.setRevocationReason(reason);
        passRepository.save(pass);

        auditLogService.log("PASS_REVOKE", "EventPass", passId.toString(),
                pass.getEvent().getId().toString(), AuditOutcome.SUCCESS, null,
                "Pass revoked. Reason: " + reason);
    }

    private void enqueueActivationEmail(EventPass pass, Attendee attendee) {
        try {
            EmailOutbox outbox = new EmailOutbox();
            outbox.setRecipientEmail(attendee.getEmail());
            outbox.setRecipientName(attendee.getFullName());
            outbox.setNotificationType(NotificationType.PASS_ACTIVATION);
            outbox.setReferenceId(pass.getId().toString());
            outbox.setStatus(EmailOutboxStatus.PENDING);
            outbox.setMaxAttempts(5);
            outbox.setPayload("{\"passNumber\":\"" + pass.getPassNumber() + "\",\"eventId\":\""
                    + pass.getEvent().getId() + "\"}");
            emailOutboxRepository.save(outbox);
        } catch (Exception e) {
            // If the outbox write fails for any reason, log and continue.
            // The pass is already activated — email can be retried manually.
            log.error("Failed to enqueue activation email for pass {}: {}", pass.getId(), e.getMessage());
        }
    }
}

