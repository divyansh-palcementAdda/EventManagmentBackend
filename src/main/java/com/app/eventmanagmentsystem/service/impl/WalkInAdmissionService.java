package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.AppException;
import com.app.eventmanagmentsystem.exceptions.EventAccessDeniedException;
import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.*;
import com.app.eventmanagmentsystem.model.enums.ActivationMode;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.model.enums.RegistrationStatus;
import com.app.eventmanagmentsystem.payload.walkin.WalkInAdmitRequest;
import com.app.eventmanagmentsystem.payload.walkin.WalkInAdmitResponse;
import com.app.eventmanagmentsystem.repository.*;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalkInAdmissionService {

    private final EventRepository eventRepository;
    private final EventDayRepository eventDayRepository;
    private final EventPassCategoryRepository eventPassCategoryRepository;
    private final AttendeeRepository attendeeRepository;
    private final EventPassRepository passRepository;
    private final CheckInRepository checkInRepository;
    private final EventGatekeeperRepository gatekeeperRepository;
    private final FormBuilderService formBuilderService;
    private final AuditLogService auditLogService;

    @PreAuthorize("hasAuthority('WALKIN_CHECKIN') or hasAuthority('CHECKIN_SCAN') or hasAuthority('SUPER_ADMIN')")
    @Transactional
    public WalkInAdmitResponse admitAttendee(WalkInAdmitRequest request) {
        AppUserDetails actor = currentUser();

        // 1. Authorize actor for this event
        validateActorAccess(request.getEventId(), actor);

        // 2. Validate event
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event", request.getEventId().toString()));
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new InvalidRequestException("Cannot admit walk-ins for a cancelled event.");
        }

        // 3. Validate Event Day
        EventDay eventDay = eventDayRepository.findById(request.getEventDayId())
                .orElseThrow(() -> new ResourceNotFoundException("EventDay", request.getEventDayId().toString()));
        if (!eventDay.getEvent().getId().equals(event.getId())) {
            throw new InvalidRequestException("Event day does not belong to the selected event.");
        }

        // 4. Validate EventPassCategory
        EventPassCategory epc = eventPassCategoryRepository.findById(request.getEventCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("EventPassCategory", request.getEventCategoryId().toString()));
        if (!epc.getEvent().getId().equals(event.getId())) {
            throw new InvalidRequestException("Category does not belong to the selected event.");
        }
        if (!epc.isActive()) {
            throw new InvalidRequestException("Category is disabled for this event.");
        }
        if (epc.getActivationMode() != ActivationMode.NO_QR_WALK_IN) {
            throw new InvalidRequestException("Category " + epc.getPassCategory().getName()
                    + " has activation mode " + epc.getActivationMode() + ", not NO_QR_WALK_IN.");
        }

        // 5. Validate dynamic schema answers
        RegistrationFormVersion version = epc.getPublishedFormVersion();
        if (version != null && request.getAnswers() != null) {
            formBuilderService.validateAnswersAgainstSchema(version, request.getAnswers());
        }

        // 6. Resolve or create Attendee
        Attendee attendee;
        if (request.getExistingAttendeeId() != null) {
            attendee = attendeeRepository.findById(request.getExistingAttendeeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Attendee", request.getExistingAttendeeId().toString()));
        } else {
            String email = request.getEmail() != null && !request.getEmail().isBlank()
                    ? request.getEmail().trim().toLowerCase() : null;

            if (email != null && attendeeRepository.existsByEventAndEmail(event.getId(), email)) {
                attendee = attendeeRepository.findByEventIdAndEmailIgnoreCase(event.getId(), email)
                        .orElseThrow(() -> new InvalidRequestException("Attendee with email already exists for this event."));
            } else {
                attendee = new Attendee();
                attendee.setEvent(event);
                attendee.setFullName(request.getFullName().trim());
                attendee.setEmail(email != null ? email : "walkin-" + UUID.randomUUID().toString().substring(0, 8) + "@event.local");
                attendee.setMobileNumber(request.getMobileNumber() != null ? request.getMobileNumber().trim() : null);
                attendee.setOrganization(request.getOrganization() != null ? request.getOrganization().trim() : null);
                attendee.setDesignation(request.getDesignation() != null ? request.getDesignation().trim() : null);
                attendee.setRegistrationStatus(RegistrationStatus.CONFIRMED);
                attendee.setRegisteredAt(Instant.now());
                attendee = attendeeRepository.save(attendee);
            }
        }

        // 7. Atomically allocate pass number and generate non-QR pass
        eventRepository.incrementPassSequenceCounter(event.getId());
        long sequence = eventRepository.getPassSequenceCounter(event.getId());
        String passNumber = String.format("%s-%s-%04d", event.getEventCode(), event.getPassSeriesCode(), sequence);

        EventPass pass = new EventPass();
        pass.setEvent(event);
        pass.setPassNumber(passNumber);
        pass.setSequenceNumber(sequence);
        pass.setCategory(epc.getPassCategory() != null ? epc.getPassCategory().getCode() : "OTHER");
        pass.setPassCategory(epc.getPassCategory());
        pass.setEventCategory(epc);
        pass.setTokenHash(null); // Explicitly null for NO_QR_WALK_IN
        pass.setStatus(PassStatus.ACTIVATED);
        pass.setGeneratedAt(Instant.now());
        pass.setActivatedAt(Instant.now());
        pass.setAttendee(attendee);
        pass.setValidDays(new HashSet<>(eventDayRepository.findByEventIdOrderByDayNumberAsc(event.getId())));

        EventPass savedPass = passRepository.save(pass);

        // 8. Save dynamic form answers if provided
        if (version != null && request.getAnswers() != null && !request.getAnswers().isEmpty()) {
            formBuilderService.saveAttendeeAnswers(attendee, savedPass, version, request.getAnswers());
        }

        // 9. Perform instant check-in
        CheckIn checkIn = new CheckIn();
        checkIn.setPass(savedPass);
        checkIn.setEvent(event);
        checkIn.setEventDay(eventDay);
        checkIn.setAttendee(attendee);
        checkIn.setCheckedInAt(Instant.now());
        checkIn.setCheckedInById(actor.getUserId().toString());
        checkIn.setCheckedInByName(actor.getFullName());

        CheckIn savedCheckIn = checkInRepository.save(checkIn);

        // 10. Audit log
        auditLogService.log("WALKIN_ADMISSION", "CheckIn", savedCheckIn.getId().toString(),
                event.getId().toString(), AuditOutcome.SUCCESS, null,
                "Admitted walk-in attendee " + attendee.getFullName() + " with pass " + passNumber);

        log.info("Walk-in admitted successfully: attendee={}, pass={}, day={}, by={}",
                attendee.getFullName(), passNumber, eventDay.getDayNumber(), actor.getEmail());

        return WalkInAdmitResponse.builder()
                .checkInId(savedCheckIn.getId())
                .passId(savedPass.getId())
                .passNumber(savedPass.getPassNumber())
                .attendeeId(attendee.getId())
                .attendeeName(attendee.getFullName())
                .attendeeEmail(attendee.getEmail())
                .eventId(event.getId())
                .eventName(event.getName())
                .eventDayId(eventDay.getId())
                .dayNumber(eventDay.getDayNumber())
                .categoryName(epc.getPassCategory() != null ? epc.getPassCategory().getName() : "Walk-In")
                .categoryCode(savedPass.getCategory())
                .admittedAt(savedCheckIn.getCheckedInAt())
                .admittedById(savedCheckIn.getCheckedInById())
                .admittedByName(savedCheckIn.getCheckedInByName())
                .message("Attendee admitted and checked in successfully.")
                .build();
    }

    private void validateActorAccess(UUID eventId, AppUserDetails actor) {
        if (actor.isSuperAdmin() || actor.hasRole("SUPER_ADMIN")) {
            return;
        }
        if (!gatekeeperRepository.existsActiveAssignment(eventId, actor.getUserId())) {
            log.warn("Walk-in admission denied: user {} does not have active assignment for event: {}",
                    actor.getEmail(), eventId);
            throw new EventAccessDeniedException();
        }
    }

    private AppUserDetails currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserDetails ud)) {
            throw new AppException("UNAUTHENTICATED", "Authentication required",
                    HttpStatus.UNAUTHORIZED);
        }
        return ud;
    }
}
