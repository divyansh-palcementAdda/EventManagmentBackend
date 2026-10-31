package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.*;
import com.app.eventmanagmentsystem.model.*;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.checkin.CheckInRequest;
import com.app.eventmanagmentsystem.payload.checkin.CheckInResponse;
import com.app.eventmanagmentsystem.repository.*;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
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
import java.util.UUID;

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
    private final AuditLogService auditLogService;

    @PreAuthorize("hasAuthority('CHECKIN_SCAN')")
    @Transactional
    public CheckInResponse performCheckIn(CheckInRequest request) {
        AppUserDetails actor = currentUser();

        // Verify gatekeeper is assigned to this event
        if (!gatekeeperRepository.existsActiveAssignment(request.getEventId(), actor.getUserId())) {
            throw new EventAccessDeniedException();
        }

        String tokenHash = qrTokenUtil.hashToken(request.getRawToken());

        // Lock pass row to prevent concurrent check-ins
        EventPass pass = passRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(InvalidQrTokenException::new);

        // Ensure pass belongs to the requested event
        if (!pass.getEvent().getId().equals(request.getEventId())) {
            throw new InvalidQrTokenException();
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
                .checkedInByName(actor.getFullName())
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

    private AppUserDetails currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserDetails ud)) {
            throw new AppException("UNAUTHENTICATED", "Authentication required",
                    org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        return ud;
    }
}

