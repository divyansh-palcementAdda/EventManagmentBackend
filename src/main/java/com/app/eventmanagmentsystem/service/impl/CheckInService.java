package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.*;
import com.app.eventmanagmentsystem.model.*;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.model.enums.CheckInPolicy;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.checkin.CheckInRequest;
import com.app.eventmanagmentsystem.payload.checkin.CheckInResponse;
import com.app.eventmanagmentsystem.payload.checkin.CheckInVerificationResponse;
import com.app.eventmanagmentsystem.payload.event.EventDayResponse;
import com.app.eventmanagmentsystem.repository.*;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import com.app.eventmanagmentsystem.util.PassLookupUtil;
import com.app.eventmanagmentsystem.util.QrTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Transactional check-in service supporting multi-day event entry.
 *
 * <p>Concurrency safety:
 * <ul>
 *   <li>The EventPass row is locked via PESSIMISTIC_WRITE before state checks.</li>
 *   <li>A unique DB constraint on check_ins (pass_id, event_day_id) prevents duplicate check-ins for the same day.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInService {

    private final EventPassRepository passRepository;
    private final CheckInRepository checkInRepository;
    private final EventGatekeeperRepository gatekeeperRepository;
    private final EventRepository eventRepository;
    private final EventDayRepository eventDayRepository;
    private final QrTokenUtil qrTokenUtil;
    private final PassLookupUtil passLookupUtil;
    private final AuditLogService auditLogService;

    @PreAuthorize("hasAuthority('CHECKIN_SCAN')")
    @Transactional(readOnly = true)
    public CheckInVerificationResponse verifyPass(CheckInRequest request) {
        AppUserDetails actor = currentUser();

        // 1. Authorize actor for this event (SUPER_ADMIN bypasses, GATEKEEPER requires active assignment)
        validateCheckInAccess(request.getEventId(), actor);

        // 2. Resolve pass
        EventPass pass = passLookupUtil.findPass(request.getRawToken())
                .orElseThrow(InvalidQrTokenException::new);

        // 3. Ensure pass belongs to the requested event
        if (!pass.getEvent().getId().equals(request.getEventId())) {
            log.warn("Pass event mismatch: pass {} belongs to event {}, but verification requested for event {}",
                    pass.getPassNumber(), pass.getEvent().getId(), request.getEventId());
            throw new PassEventMismatchException();
        }

        Event event = pass.getEvent();
        EventDay eventDay = resolveEventDay(event, request.getEventDayId());

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

        Attendee attendee = pass.getAttendee();
        String attendeeName = attendee != null ? attendee.getFullName() : null;
        String attendeeEmail = attendee != null ? attendee.getEmail() : null;
        String attendeeOrg = attendee != null ? attendee.getOrganization() : null;
        String attendeeDesig = attendee != null ? attendee.getDesignation() : null;
        UUID attendeeId = attendee != null ? attendee.getId() : null;

        CheckInVerificationResponse.CheckInVerificationResponseBuilder builder =
                CheckInVerificationResponse.builder()
                        .passId(pass.getId())
                        .passNumber(pass.getPassNumber())
                        .eventId(event.getId())
                        .eventName(event.getName())
                        .eventCode(event.getEventCode())
                        .venue(event.getVenue())
                        .eventDayId(eventDay.getId())
                        .dayNumber(eventDay.getDayNumber())
                        .eventDate(eventDay.getEventDate() != null ? eventDay.getEventDate().toString() : null)
                        .eventDayLabel(eventDay.getLabel())
                        .attendeeId(attendeeId)
                        .attendeeName(attendeeName)
                        .attendeeEmail(attendeeEmail)
                        .attendeeOrganization(attendeeOrg)
                        .attendeeDesignation(attendeeDesig)
                        .validDays(validDays);

        // Check REVOKED
        if (pass.getStatus() == PassStatus.REVOKED) {
            return builder
                    .valid(false)
                    .eligibleForCheckIn(false)
                    .status("REVOKED")
                    .message("Pass has been revoked: " + (pass.getRevocationReason() != null ? pass.getRevocationReason() : "Revoked by administrator"))
                    .build();
        }

        // Check NOT_ACTIVATED
        if (pass.getStatus() == PassStatus.NOT_ACTIVATED || attendee == null) {
            return builder
                    .valid(true)
                    .eligibleForCheckIn(false)
                    .status("NOT_ACTIVATED")
                    .message("This pass has not been activated yet. The attendee must activate the pass before entry.")
                    .build();
        }

        // Check Day validity
        if (pass.getValidDays() != null && !pass.getValidDays().isEmpty()) {
            boolean validForDay = pass.getValidDays().stream()
                    .anyMatch(d -> d.getId().equals(eventDay.getId()));
            if (!validForDay) {
                return builder
                        .valid(true)
                        .eligibleForCheckIn(false)
                        .status("NOT_VALID_TODAY")
                        .message("Pass is not valid for " + eventDay.getLabel() + " (Day " + eventDay.getDayNumber() + ").")
                        .build();
            }
        }

        // Check Existing Check-In for this event day
        Optional<CheckIn> existingCheckIn = checkInRepository.findByPassIdAndEventDayId(pass.getId(), eventDay.getId());
        if (existingCheckIn.isPresent()) {
            CheckIn ci = existingCheckIn.get();
            return builder
                    .valid(true)
                    .eligibleForCheckIn(false)
                    .status("ALREADY_CHECKED_IN")
                    .alreadyCheckedIn(true)
                    .checkedInAt(ci.getCheckedInAt())
                    .checkedInByName(ci.getCheckedInByName())
                    .message("Pass has already been checked in for " + eventDay.getLabel() + ".")
                    .build();
        }

        // All checks passed!
        return builder
                .valid(true)
                .eligibleForCheckIn(true)
                .status("READY_FOR_CHECKIN")
                .alreadyCheckedIn(false)
                .message("Pass is valid and ready for check-in.")
                .build();
    }

    @PreAuthorize("hasAuthority('CHECKIN_SCAN')")
    @Transactional
    public CheckInResponse performCheckIn(CheckInRequest request) {
        AppUserDetails actor = currentUser();

        // Authorize actor for this event (SUPER_ADMIN bypasses, GATEKEEPER requires active assignment)
        validateCheckInAccess(request.getEventId(), actor);

        // Lock pass row to prevent concurrent check-ins
        EventPass pass = passLookupUtil.findPassForUpdate(request.getRawToken())
                .orElseThrow(InvalidQrTokenException::new);

        // Ensure pass belongs to the requested event
        if (!pass.getEvent().getId().equals(request.getEventId())) {
            log.warn("Pass event mismatch: pass {} belongs to event {}, but check-in requested for event {}",
                    pass.getPassNumber(), pass.getEvent().getId(), request.getEventId());
            throw new PassEventMismatchException();
        }

        // State checks — must be done while holding the lock
        if (pass.getStatus() == PassStatus.REVOKED) {
            throw new PassRevokedException();
        }
        if (pass.getStatus() == PassStatus.NOT_ACTIVATED) {
            throw new PassNotActivatedException();
        }

        // Attendee must be present (pass must be activated)
        Attendee attendee = pass.getAttendee();
        if (attendee == null) {
            throw new PassNotActivatedException();
        }

        // Resolve EventDay
        EventDay eventDay = resolveEventDay(pass.getEvent(), request.getEventDayId());

        // Validate pass validity for this day
        if (pass.getValidDays() != null && !pass.getValidDays().isEmpty()) {
            boolean validForDay = pass.getValidDays().stream()
                    .anyMatch(d -> d.getId().equals(eventDay.getId()));
            if (!validForDay) {
                throw new PassNotValidForDayException(
                        "Pass is not valid for " + eventDay.getLabel() + " (Day " + eventDay.getDayNumber() + ")");
            }
        }

        // Check for existing check-in for this pass on this specific event day
        if (checkInRepository.existsByPassIdAndEventDayId(pass.getId(), eventDay.getId())) {
            throw new PassAlreadyCheckedInException("Pass has already been checked in for " + eventDay.getLabel());
        }

        Instant now = Instant.now();
        CheckIn checkIn = new CheckIn();
        checkIn.setEvent(pass.getEvent());
        checkIn.setPass(pass);
        checkIn.setAttendee(attendee);
        checkIn.setEventDay(eventDay);
        checkIn.setCheckedInById(actor.getUserId().toString());
        checkIn.setCheckedInByName(actor.getFullName());
        checkIn.setCheckedInAt(now);
        checkIn.setRequestId(request.getRequestId());

        CheckIn saved = checkInRepository.save(checkIn);

        auditLogService.log("CHECKIN", "CheckIn", saved.getId().toString(),
                pass.getEvent().getId().toString(), AuditOutcome.SUCCESS,
                request.getRequestId(),
                "Pass checked in: " + pass.getPassNumber() + " for " + eventDay.getLabel());

        return toCheckInResponse(saved, pass, attendee, eventDay, actor);
    }

    private CheckInResponse toCheckInResponse(CheckIn saved, EventPass pass, Attendee attendee, EventDay eventDay, AppUserDetails actor) {
        Event event = pass.getEvent();
        return CheckInResponse.builder()
                .checkInId(saved.getId())
                .passId(pass.getId())
                .passNumber(pass.getPassNumber())
                .attendeeId(attendee.getId())
                .attendeeName(attendee.getFullName())
                .attendeeEmail(attendee.getEmail())
                .eventId(event.getId())
                .eventName(event.getName())
                .eventDayId(eventDay.getId())
                .dayNumber(eventDay.getDayNumber())
                .eventDate(eventDay.getEventDate() != null ? eventDay.getEventDate().toString() : null)
                .eventDayLabel(eventDay.getLabel())
                .checkedInAt(saved.getCheckedInAt())
                .checkedInByName(saved.getCheckedInByName() != null ? saved.getCheckedInByName() : actor.getFullName())
                .build();
    }

    private EventDay resolveEventDay(Event event, UUID requestedDayId) {
        List<EventDay> days = eventDayRepository.findByEventIdOrderByDayNumberAsc(event.getId());
        if (days.isEmpty()) {
            LocalDate today = LocalDate.now(ZoneId.of("UTC"));
            EventDay day = new EventDay(event, 1, today, "Day 1 — " + today);
            return eventDayRepository.save(day);
        }

        if (requestedDayId != null) {
            return days.stream()
                    .filter(d -> d.getId().equals(requestedDayId))
                    .findFirst()
                    .orElseThrow(() -> new InvalidRequestException("Event day not found for event " + event.getEventCode()));
        }

        // If not specified, resolve by today's date in event timezone
        ZoneId zone = ZoneId.of("UTC");
        try {
            if (event.getTimezone() != null && !event.getTimezone().isBlank()) {
                zone = ZoneId.of(event.getTimezone());
            }
        } catch (Exception ignored) {}

        LocalDate today = LocalDate.now(zone);
        return days.stream()
                .filter(d -> d.getEventDate() != null && d.getEventDate().equals(today))
                .findFirst()
                .orElse(days.get(0));
    }

    private void validateCheckInAccess(UUID eventId, AppUserDetails actor) {
        if (eventId == null || !eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", String.valueOf(eventId));
        }

        // Global administrative bypass for SUPER_ADMIN
        if (actor.isSuperAdmin() || actor.hasRole("SUPER_ADMIN")) {
            log.info("Check-in access granted globally for SUPER_ADMIN: {} on event: {}",
                    actor.getEmail(), eventId);
            return;
        }

        // Ordinary gatekeeper scoped verification
        if (!gatekeeperRepository.existsActiveAssignment(eventId, actor.getUserId())) {
            log.warn("Check-in access denied: user {} does not have active gatekeeper assignment for event: {}",
                    actor.getEmail(), eventId);
            throw new EventAccessDeniedException();
        }
    }

    private AppUserDetails currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserDetails ud)) {
            throw new AppException("UNAUTHENTICATED", "Authentication required",
                    org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        return ud;
    }
}

