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
import java.util.UUID;

/**
 * Transactional, single-use check-in service.
 *
 * <p>Concurrency safety:
 * <ul>
 *   <li>The EventPass row is locked via PESSIMISTIC_WRITE before state checks.</li>
 *   <li>A unique DB constraint on check_ins.pass_id prevents duplicate successful records.</li>
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

        // Check for existing successful check-in (covers the race condition + duplicate scan)
        if (checkInRepository.existsByPassId(pass.getId())) {
            throw new PassAlreadyCheckedInException();
        }

        // Attendee must be present (pass must be activated)
        Attendee attendee = pass.getAttendee();
        if (attendee == null) {
            throw new PassNotActivatedException();
        }

        Instant now = Instant.now();
        CheckIn checkIn = new CheckIn();
        checkIn.setEvent(pass.getEvent());
        checkIn.setPass(pass);
        checkIn.setAttendee(attendee);
        checkIn.setCheckedInById(actor.getUserId().toString());
        checkIn.setCheckedInByName(actor.getFullName());
        checkIn.setCheckedInAt(now);
        checkIn.setRequestId(request.getRequestId());

        CheckIn saved = checkInRepository.save(checkIn);

        auditLogService.log("CHECKIN", "CheckIn", saved.getId().toString(),
                pass.getEvent().getId().toString(), AuditOutcome.SUCCESS,
                request.getRequestId(),
                "Pass checked in: " + pass.getPassNumber());

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
                .checkedInAt(saved.getCheckedInAt())
                .checkedInByName(actor.getFullName())
                .build();
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

