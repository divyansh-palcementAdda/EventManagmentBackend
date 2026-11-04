package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.*;
import com.app.eventmanagmentsystem.model.*;
import com.app.eventmanagmentsystem.model.enums.*;
import com.app.eventmanagmentsystem.payload.category.FormSchemaResponse;
import com.app.eventmanagmentsystem.payload.event.EventDayResponse;
import com.app.eventmanagmentsystem.payload.pass.ActivatePassRequest;
import com.app.eventmanagmentsystem.payload.pass.AutoActivatePassRequest;
import com.app.eventmanagmentsystem.payload.pass.PassResponse;
import com.app.eventmanagmentsystem.payload.pass.ValidatePassResponse;
import com.app.eventmanagmentsystem.repository.*;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import com.app.eventmanagmentsystem.util.PassLookupUtil;
import com.app.eventmanagmentsystem.util.QrTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles transactional pass activation.
 *
 * <p>Within a single transaction:
 * <ol>
 *   <li>Lock the pass row (PESSIMISTIC_WRITE) to prevent concurrent activation.</li>
 *   <li>Validate the token hash.</li>
 *   <li>Validate pass state (must be NOT_ACTIVATED).</li>
 *   <li>Create the Attendee record (if identity collection required).</li>
 *   <li>Associate the attendee with the pass.</li>
 *   <li>Update pass status to ACTIVATED.</li>
 *   <li>Save dynamic form answers to attendee_registration_answers.</li>
 *   <li>Persist an email outbox entry (same transaction).</li>
 * </ol>
 * Email delivery failures do not undo activation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PassActivationService {

    private final EventPassRepository passRepository;
    private final AttendeeRepository attendeeRepository;
    private final EventRepository eventRepository;
    private final EventDayRepository eventDayRepository;
    private final EventPassCategoryRepository eventPassCategoryRepository;
    private final PassCategoryRepository passCategoryRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final FormBuilderService formBuilderService;
    private final QrTokenUtil qrTokenUtil;
    private final PassLookupUtil passLookupUtil;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public ValidatePassResponse validatePass(String rawToken) {
        EventPass pass = passLookupUtil.findPass(rawToken)
                .orElseThrow(InvalidQrTokenException::new);

        Event event = pass.getEvent();
        Instant now = Instant.now();

        boolean eligible = true;
        String message = "Pass is valid and ready for activation.";

        // Resolve EventPassCategory & policies
        EventPassCategory epc = resolveEventCategory(pass, event);
        ActivationMode activationMode = epc != null ? epc.getActivationMode() : ActivationMode.SELF_SERVICE;
        IdentityPolicy identityPolicy = epc != null ? epc.getIdentityPolicy() : IdentityPolicy.COLLECT_IDENTITY;
        String categoryName = epc != null && epc.getPassCategory() != null ? epc.getPassCategory().getName()
                : (pass.getPassCategory() != null ? pass.getPassCategory().getName() : pass.getCategory());
        String categoryCode = pass.getCategory();

        if (pass.getStatus() == PassStatus.REVOKED) {
            eligible = false;
            message = "This pass has been revoked: " + (pass.getRevocationReason() != null ? pass.getRevocationReason() : "Revoked by administrator");
        } else if (pass.getStatus() == PassStatus.ACTIVATED) {
            eligible = false;
            message = "This pass has already been activated.";
        } else if (activationMode == ActivationMode.NO_QR_WALK_IN) {
            eligible = false;
            message = "Walk-in Only Pass — Admission handled on-site by staff.";
        } else {
            if (event.getActivationStartsAt() != null && now.isBefore(event.getActivationStartsAt())) {
                eligible = false;
                message = "Pass activation window has not opened yet.";
            } else if (event.getActivationEndsAt() != null && now.isAfter(event.getActivationEndsAt())) {
                eligible = false;
                message = "Pass activation window has closed.";
            }
        }

        List<EventDayResponse> validDays = pass.getValidDays() != null && !pass.getValidDays().isEmpty()
                ? pass.getValidDays().stream()
                        .map(d -> EventDayResponse.builder()
                                .id(d.getId())
                                .dayNumber(d.getDayNumber())
                                .eventDate(d.getEventDate())
                                .label(d.getLabel())
                                .build())
                        .collect(Collectors.toList())
                : eventDayRepository.findByEventIdOrderByDayNumberAsc(event.getId()).stream()
                        .map(d -> EventDayResponse.builder()
                                .id(d.getId())
                                .dayNumber(d.getDayNumber())
                                .eventDate(d.getEventDate())
                                .label(d.getLabel())
                                .build())
                        .collect(Collectors.toList());

        String attendeeName = pass.getAttendee() != null ? pass.getAttendee().getFullName() : null;
        String attendeeEmail = pass.getAttendee() != null ? pass.getAttendee().getEmail() : null;

        FormSchemaResponse formSchema = null;
        if (epc != null) {
            formSchema = formBuilderService.getPublishedFormSchema(epc.getId());
        }

        return ValidatePassResponse.builder()
                .valid(true)
                .passId(pass.getId())
                .passNumber(pass.getPassNumber())
                .category(pass.getCategory())
                .categoryCode(categoryCode)
                .categoryName(categoryName)
                .activationMode(activationMode)
                .identityPolicy(identityPolicy)
                .formSchema(formSchema)
                .status(pass.getStatus())
                .eventId(event.getId())
                .eventName(event.getName())
                .eventCode(event.getEventCode())
                .venue(event.getVenue())
                .startsAt(event.getStartsAt())
                .endsAt(event.getEndsAt())
                .validDays(validDays)
                .isEligibleForActivation(eligible)
                .message(message)
                .attendeeName(pass.getStatus() == PassStatus.ACTIVATED ? null : attendeeName)
                .attendeeEmail(pass.getStatus() == PassStatus.ACTIVATED ? null : attendeeEmail)
                .build();
    }

    @Transactional
    public PassResponse activatePass(ActivatePassRequest request) {
        // Lock the pass row before any state checks
        EventPass pass = passLookupUtil.findPassForUpdate(request.getRawToken())
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

        EventPassCategory epc = resolveEventCategory(pass, event);
        if (epc != null && epc.getActivationMode() == ActivationMode.NO_QR_WALK_IN) {
            throw new InvalidRequestException("Walk-in passes cannot be self-activated via QR.");
        }

        // Validate dynamic schema answers if form version is published
        RegistrationFormVersion version = epc != null ? epc.getPublishedFormVersion() : null;
        if (version != null && request.getAnswers() != null) {
            formBuilderService.validateAnswersAgainstSchema(version, request.getAnswers());
        }

        // Prevent duplicate attendee-email per event
        String rawEmail = request.getAttendeeEmail();
        if ((rawEmail == null || rawEmail.isBlank()) && request.getAnswers() != null && request.getAnswers().containsKey("email")) {
            rawEmail = String.valueOf(request.getAnswers().get("email"));
        }
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new InvalidRequestException("Attendee email is required.");
        }
        String normalizedEmail = rawEmail.toLowerCase().trim();

        if (attendeeRepository.existsByEventAndEmail(event.getId(), normalizedEmail)) {
            throw new DuplicateResourceException(
                    "An attendee with this email is already registered for this event.");
        }

        String rawFullName = request.getAttendeeFullName();
        if ((rawFullName == null || rawFullName.isBlank()) && request.getAnswers() != null && request.getAnswers().containsKey("fullName")) {
            rawFullName = String.valueOf(request.getAnswers().get("fullName"));
        }
        if (rawFullName == null || rawFullName.isBlank()) {
            throw new InvalidRequestException("Attendee full name is required.");
        }

        // Create attendee
        Attendee attendee = new Attendee();
        attendee.setEvent(event);
        attendee.setFullName(rawFullName.trim());
        attendee.setEmail(normalizedEmail);
        attendee.setMobileNumber(request.getAttendeeMobileNumber() != null ? request.getAttendeeMobileNumber().trim() : null);

        if ("STUDENT".equalsIgnoreCase(pass.getCategory())) {
            String college = request.getAttendeeCollege() != null && !request.getAttendeeCollege().isBlank()
                    ? request.getAttendeeCollege().trim()
                    : (request.getAttendeeOrganization() != null ? request.getAttendeeOrganization().trim() : "");
            if (college.isBlank() && request.getAnswers() != null && request.getAnswers().containsKey("college")) {
                college = String.valueOf(request.getAnswers().get("college")).trim();
            }
            if (college.isBlank()) {
                throw new InvalidRequestException("College or institution is required for student pass registration.");
            }
            String course = request.getAttendeeCourse() != null ? request.getAttendeeCourse().trim() : "";
            if (course.isBlank() && request.getAnswers() != null && request.getAnswers().containsKey("course")) {
                course = String.valueOf(request.getAnswers().get("course")).trim();
            }
            if (course.isBlank()) {
                throw new InvalidRequestException("Course or program is required for student pass registration.");
            }
            attendee.setCollege(college);
            attendee.setOrganization(college);
            attendee.setCourse(course);
            if (request.getAttendeeYearSemester() != null && !request.getAttendeeYearSemester().isBlank()) {
                attendee.setYearSemester(request.getAttendeeYearSemester().trim());
            }
            if (request.getAttendeeStudentId() != null && !request.getAttendeeStudentId().isBlank()) {
                attendee.setStudentId(request.getAttendeeStudentId().trim());
            }
            attendee.setDesignation(request.getAttendeeDesignation() != null && !request.getAttendeeDesignation().isBlank()
                    ? request.getAttendeeDesignation().trim() : "Student");
        } else {
            if (request.getAttendeeOrganization() != null && !request.getAttendeeOrganization().isBlank()) {
                attendee.setOrganization(request.getAttendeeOrganization().trim());
            }
            if (request.getAttendeeDesignation() != null && !request.getAttendeeDesignation().isBlank()) {
                attendee.setDesignation(request.getAttendeeDesignation().trim());
            }
        }

        attendee.setRegistrationStatus(RegistrationStatus.CONFIRMED);
        attendee.setRegisteredAt(now);
        Attendee savedAttendee = attendeeRepository.save(attendee);

        // Activate pass
        pass.setAttendee(savedAttendee);
        pass.setStatus(PassStatus.ACTIVATED);
        pass.setActivatedAt(now);
        EventPass savedPass = passRepository.save(pass);

        // Save dynamic form answers if present
        if (request.getAnswers() != null && !request.getAnswers().isEmpty()) {
            formBuilderService.saveAttendeeAnswers(savedAttendee, savedPass, version, request.getAnswers());
        }

        // Enqueue activation email
        enqueueActivationEmail(savedPass, savedAttendee);

        auditLogService.log("PASS_ACTIVATE", "EventPass", pass.getId().toString(),
                event.getId().toString(), AuditOutcome.SUCCESS, null,
                "Pass activated by attendee: " + normalizedEmail);

        String catName = pass.getPassCategory() != null ? pass.getPassCategory().getName() : pass.getCategory();
        return PassResponse.builder()
                .id(savedPass.getId())
                .passNumber(savedPass.getPassNumber())
                .sequenceNumber(savedPass.getSequenceNumber())
                .category(savedPass.getCategory())
                .categoryName(catName)
                .status(savedPass.getStatus())
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .generatedAt(savedPass.getGeneratedAt())
                .activatedAt(savedPass.getActivatedAt())
                .attendeeName(savedAttendee.getFullName())
                .attendeeEmail(savedAttendee.getEmail())
                .attendeeMobileNumber(savedAttendee.getMobileNumber())
                .rawToken(null)
                .build();
    }

    @Transactional
    public PassResponse autoActivatePass(AutoActivatePassRequest request) {
        EventPass pass = passLookupUtil.findPassForUpdate(request.getRawToken())
                .orElseThrow(InvalidQrTokenException::new);

        if (!pass.getEvent().getId().equals(request.getEventId())) {
            throw new InvalidQrTokenException();
        }

        if (pass.getStatus() == PassStatus.REVOKED) {
            throw new PassRevokedException();
        }

        // Idempotency: if already activated, return existing state safely
        if (pass.getStatus() == PassStatus.ACTIVATED) {
            log.info("Pass {} is already activated — idempotent auto-activation", pass.getPassNumber());
            return toActivatedResponse(pass);
        }

        Event event = pass.getEvent();
        Instant now = Instant.now();
        if (event.getActivationStartsAt() != null && now.isBefore(event.getActivationStartsAt())) {
            throw new InvalidRequestException("Pass activation window has not opened yet.");
        }
        if (event.getActivationEndsAt() != null && now.isAfter(event.getActivationEndsAt())) {
            throw new InvalidRequestException("Pass activation window has closed.");
        }

        EventPassCategory epc = resolveEventCategory(pass, event);
        if (epc != null && epc.getActivationMode() != ActivationMode.SCAN_AUTO_ACTIVATE) {
            throw new InvalidRequestException("Pass category does not support scan auto-activation. Current mode: " + epc.getActivationMode());
        }

        pass.setStatus(PassStatus.ACTIVATED);
        pass.setActivatedAt(now);
        pass.setAttendee(null); // No attendee identity collected
        EventPass savedPass = passRepository.save(pass);

        auditLogService.log("PASS_AUTO_ACTIVATE", "EventPass", savedPass.getId().toString(),
                event.getId().toString(), AuditOutcome.SUCCESS, null,
                "Pass auto-activated via scan without attendee identity.");

        log.info("Pass {} auto-activated successfully without attendee identity", savedPass.getPassNumber());
        return toActivatedResponse(savedPass);
    }

    private PassResponse toActivatedResponse(EventPass pass) {
        String attendeeName = pass.getAttendee() != null ? pass.getAttendee().getFullName() : "No Identity (Auto-Activated)";
        String attendeeEmail = pass.getAttendee() != null ? pass.getAttendee().getEmail() : null;
        String attendeeMobile = pass.getAttendee() != null ? pass.getAttendee().getMobileNumber() : null;
        String catName = pass.getPassCategory() != null ? pass.getPassCategory().getName() : pass.getCategory();

        return PassResponse.builder()
                .id(pass.getId())
                .passNumber(pass.getPassNumber())
                .sequenceNumber(pass.getSequenceNumber())
                .category(pass.getCategory())
                .categoryName(catName)
                .activationMode(pass.getEventCategory() != null ? pass.getEventCategory().getActivationMode() : ActivationMode.SCAN_AUTO_ACTIVATE)
                .qrRequired(pass.getEventCategory() == null || pass.getEventCategory().isQrRequired())
                .status(pass.getStatus())
                .eventId(pass.getEvent().getId())
                .eventCode(pass.getEvent().getEventCode())
                .generatedAt(pass.getGeneratedAt())
                .activatedAt(pass.getActivatedAt())
                .attendeeName(attendeeName)
                .attendeeEmail(attendeeEmail)
                .attendeeMobileNumber(attendeeMobile)
                .rawToken(null)
                .build();
    }

    private EventPassCategory resolveEventCategory(EventPass pass, Event event) {
        EventPassCategory epc = pass.getEventCategory();
        if (epc == null && pass.getPassCategory() != null) {
            epc = eventPassCategoryRepository.findByEventIdAndPassCategoryId(event.getId(), pass.getPassCategory().getId()).orElse(null);
        }
        if (epc == null && pass.getCategory() != null) {
            com.app.eventmanagmentsystem.model.PassCategory cat = passCategoryRepository.findByCodeIgnoreCase(pass.getCategory()).orElse(null);
            if (cat != null) {
                epc = eventPassCategoryRepository.findByEventIdAndPassCategoryId(event.getId(), cat.getId()).orElse(null);
            }
        }
        return epc;
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
            String catName = pass.getCategory() != null ? pass.getCategory() : "STUDENT";
            String safeEventName = pass.getEvent() != null && pass.getEvent().getName() != null
                    ? pass.getEvent().getName().replace("\"", "\\\"") : "";
            String safeAttendeeName = attendee.getFullName() != null
                    ? attendee.getFullName().replace("\"", "\\\"") : "";
            outbox.setPayload(String.format(
                    "{\"passNumber\":\"%s\",\"category\":\"%s\",\"eventId\":\"%s\",\"eventName\":\"%s\",\"attendeeName\":\"%s\"}",
                    pass.getPassNumber(), catName, pass.getEvent().getId(), safeEventName, safeAttendeeName));
            emailOutboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to enqueue activation email for pass {}: {}", pass.getId(), e.getMessage());
        }
    }
}
