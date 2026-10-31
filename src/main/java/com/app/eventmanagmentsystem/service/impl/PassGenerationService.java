package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.CheckIn;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventDay;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.event.EventDayResponse;
import com.app.eventmanagmentsystem.payload.pass.GeneratePassRequest;
import com.app.eventmanagmentsystem.payload.pass.PassResponse;
import com.app.eventmanagmentsystem.repository.CheckInRepository;
import com.app.eventmanagmentsystem.repository.EventDayRepository;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.repository.specification.PassSpecification;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import com.app.eventmanagmentsystem.util.QrTokenUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pass generation service.
 *
 * <p>Pass number format: {EVENT_CODE}-{SERIES_CODE}-{SEQUENCE}
 * <p>Example: RFC-F5EE57F3-0001
 *
 * <p>Concurrency safety:
 * <ul>
 *   <li>The event row is locked (PESSIMISTIC_WRITE) before incrementing the counter.</li>
 *   <li>The unique DB constraint on pass_number provides an additional safety net.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PassGenerationService {

    private final EventRepository eventRepository;
    private final EventDayRepository eventDayRepository;
    private final EventPassRepository passRepository;
    private final CheckInRepository checkInRepository;
    private final QrTokenUtil qrTokenUtil;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;

    @PreAuthorize("hasAuthority('PASS_GENERATE')")
    @Transactional
    public List<PassResponse> generatePasses(GeneratePassRequest request) {
        // Idempotency check for single-pass requests
        if (request.getBatchSize() == 1 && request.getIdempotencyKey() != null) {
            var existing = passRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotent pass generation request — returning existing pass");
                return List.of(toResponseWithoutToken(existing.get()));
            }
        }

        // Lock the event row to serialize sequence counter updates
        Event event = entityManager.find(Event.class, request.getEventId(), LockModeType.PESSIMISTIC_WRITE);
        if (event == null) {
            throw new ResourceNotFoundException("Event", request.getEventId().toString());
        }

        validateEventForPassGeneration(event, request.getBatchSize());

        // Resolve permitted event days
        Set<EventDay> permittedDays = resolvePermittedDays(event, request.getValidDayIds());

        List<PassResponse> results = new ArrayList<>(request.getBatchSize());
        for (int i = 0; i < request.getBatchSize(); i++) {
            PassResponse passResponse = generateSinglePass(event, request.getIdempotencyKey(), i, permittedDays);
            results.add(passResponse);
        }

        auditLogService.log("PASS_GENERATE", "EventPass", null,
                event.getId().toString(), AuditOutcome.SUCCESS, null,
                "Generated " + request.getBatchSize() + " passes for event " + event.getEventCode());
        return results;
    }

    private Set<EventDay> resolvePermittedDays(Event event, List<UUID> requestedDayIds) {
        List<EventDay> allDays = eventDayRepository.findByEventIdOrderByDayNumberAsc(event.getId());
        if (requestedDayIds == null || requestedDayIds.isEmpty()) {
            return new HashSet<>(allDays);
        }

        Set<UUID> validIds = allDays.stream().map(EventDay::getId).collect(Collectors.toSet());
        for (UUID requestedId : requestedDayIds) {
            if (!validIds.contains(requestedId)) {
                throw new InvalidRequestException("Event day ID " + requestedId + " does not belong to event " + event.getEventCode());
            }
        }

        return allDays.stream()
                .filter(d -> requestedDayIds.contains(d.getId()))
                .collect(Collectors.toSet());
    }

    private PassResponse generateSinglePass(Event event, String idempotencyKey, int batchIndex, Set<EventDay> permittedDays) {
        // Increment sequence counter atomically within the locked transaction
        eventRepository.incrementPassSequenceCounter(event.getId());
        // Re-read the counter after increment
        long sequence = eventRepository.getPassSequenceCounter(event.getId());

        String passNumber = formatPassNumber(event.getEventCode(), event.getPassSeriesCode(), sequence);

        String rawToken = qrTokenUtil.generateRawToken();
        String tokenHash = qrTokenUtil.hashToken(rawToken);

        EventPass pass = new EventPass();
        pass.setEvent(event);
        pass.setPassNumber(passNumber);
        pass.setSequenceNumber(sequence);
        pass.setTokenHash(tokenHash);
        pass.setStatus(PassStatus.NOT_ACTIVATED);
        pass.setGeneratedAt(Instant.now());
        pass.setValidDays(new HashSet<>(permittedDays));

        if (batchIndex == 0 && idempotencyKey != null) {
            pass.setIdempotencyKey(idempotencyKey);
        }

        EventPass saved = passRepository.save(pass);
        // Raw token returned ONLY here, at generation time
        return toResponseWithToken(saved, rawToken, event);
    }

    private void validateEventForPassGeneration(Event event, int batchSize) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new InvalidRequestException("Cannot generate passes for a cancelled event.");
        }
        if (event.getMaximumPasses() != null) {
            long currentCount = passRepository.countByEventId(event.getId());
            if (currentCount + batchSize > event.getMaximumPasses()) {
                throw new InvalidRequestException(
                        "Generating " + batchSize + " passes would exceed the maximum limit of "
                                + event.getMaximumPasses() + " (current: " + currentCount + ").");
            }
        }
    }

    private String formatPassNumber(String eventCode, String seriesCode, long sequence) {
        return String.format("%s-%s-%04d", eventCode, seriesCode, sequence);
    }

    @PreAuthorize("hasAuthority('PASS_VIEW')")
    @Transactional(readOnly = true)
    public Page<PassResponse> listPassesByEvent(UUID eventId, Pageable pageable) {
        return searchPassesByEvent(eventId, null, null, null, null, null, null, pageable);
    }

    @PreAuthorize("hasAuthority('PASS_VIEW')")
    @Transactional(readOnly = true)
    public Page<PassResponse> searchPassesByEvent(
            UUID eventId,
            String search,
            PassStatus status,
            String checkInStatus,
            UUID eventDayId,
            Long fromSequence,
            Long toSequence,
            Pageable pageable) {

        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", eventId.toString());
        }

        EventDay eventDay = null;
        if (eventDayId != null) {
            eventDay = eventDayRepository.findById(eventDayId)
                    .orElseThrow(() -> new ResourceNotFoundException("EventDay", eventDayId.toString()));
        }

        Set<UUID> checkedInPassIds = null;
        if (checkInStatus != null && !checkInStatus.isBlank() && !"ALL".equalsIgnoreCase(checkInStatus)) {
            checkedInPassIds = checkInRepository.findCheckedInPassIdsByEventId(eventId);
        }

        Specification<EventPass> spec = PassSpecification.filterPasses(
                eventId, search, status, fromSequence, toSequence, eventDay, checkInStatus, checkedInPassIds);

        Page<EventPass> page = passRepository.findAll(spec, pageable);

        List<UUID> pagePassIds = page.getContent().stream().map(EventPass::getId).collect(Collectors.toList());
        Map<UUID, List<CheckIn>> checkInsByPass = pagePassIds.isEmpty() ? Collections.emptyMap() :
                checkInRepository.findByPassIdIn(pagePassIds).stream()
                        .collect(Collectors.groupingBy(c -> c.getPass().getId()));

        return page.map(pass -> toResponseWithCheckInInfo(pass, checkInsByPass.get(pass.getId())));
    }

    @PreAuthorize("hasAuthority('PASS_VIEW')")
    @Transactional(readOnly = true)
    public PassResponse getPass(UUID passId) {
        EventPass pass = passRepository.findById(passId)
                .orElseThrow(() -> new ResourceNotFoundException("EventPass", passId.toString()));
        return toResponseWithoutToken(pass);
    }

    private List<EventDayResponse> mapDays(Set<EventDay> days) {
        if (days == null) return Collections.emptyList();
        return days.stream()
                .sorted(Comparator.comparingInt(EventDay::getDayNumber))
                .map(d -> EventDayResponse.builder()
                        .id(d.getId())
                        .dayNumber(d.getDayNumber())
                        .eventDate(d.getEventDate())
                        .label(d.getLabel())
                        .build())
                .collect(Collectors.toList());
    }

    private PassResponse toResponseWithToken(EventPass pass, String rawToken, Event event) {
        String attendeeName = pass.getAttendee() != null ? pass.getAttendee().getFullName() : null;
        String attendeeEmail = pass.getAttendee() != null ? pass.getAttendee().getEmail() : null;
        String attendeeMobile = pass.getAttendee() != null ? pass.getAttendee().getMobileNumber() : null;

        return PassResponse.builder()
                .id(pass.getId())
                .passNumber(pass.getPassNumber())
                .sequenceNumber(pass.getSequenceNumber())
                .status(pass.getStatus())
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .generatedAt(pass.getGeneratedAt())
                .activatedAt(pass.getActivatedAt())
                .attendeeName(attendeeName)
                .attendeeEmail(attendeeEmail)
                .attendeeMobileNumber(attendeeMobile)
                .checkedIn(false)
                .checkInCount(0L)
                .validDays(mapDays(pass.getValidDays()))
                .rawToken(rawToken)
                .build();
    }

    private PassResponse toResponseWithoutToken(EventPass pass) {
        String attendeeName = pass.getAttendee() != null ? pass.getAttendee().getFullName() : null;
        String attendeeEmail = pass.getAttendee() != null ? pass.getAttendee().getEmail() : null;
        String attendeeMobile = pass.getAttendee() != null ? pass.getAttendee().getMobileNumber() : null;
        boolean isCheckedIn = checkInRepository.existsByPassId(pass.getId());

        return PassResponse.builder()
                .id(pass.getId())
                .passNumber(pass.getPassNumber())
                .sequenceNumber(pass.getSequenceNumber())
                .status(pass.getStatus())
                .eventId(pass.getEvent().getId())
                .eventCode(pass.getEvent().getEventCode())
                .generatedAt(pass.getGeneratedAt())
                .activatedAt(pass.getActivatedAt())
                .attendeeName(attendeeName)
                .attendeeEmail(attendeeEmail)
                .attendeeMobileNumber(attendeeMobile)
                .checkedIn(isCheckedIn)
                .checkInCount(isCheckedIn ? 1L : 0L)
                .validDays(mapDays(pass.getValidDays()))
                .rawToken(null)
                .build();
    }

    private PassResponse toResponseWithCheckInInfo(EventPass pass, List<CheckIn> checkIns) {
        String attendeeName = pass.getAttendee() != null ? pass.getAttendee().getFullName() : null;
        String attendeeEmail = pass.getAttendee() != null ? pass.getAttendee().getEmail() : null;
        String attendeeMobile = pass.getAttendee() != null ? pass.getAttendee().getMobileNumber() : null;
        boolean isCheckedIn = checkIns != null && !checkIns.isEmpty();
        long count = checkIns != null ? checkIns.size() : 0L;
        Instant lastCheckIn = isCheckedIn ? checkIns.get(0).getCheckedInAt() : null;

        return PassResponse.builder()
                .id(pass.getId())
                .passNumber(pass.getPassNumber())
                .sequenceNumber(pass.getSequenceNumber())
                .status(pass.getStatus())
                .eventId(pass.getEvent().getId())
                .eventCode(pass.getEvent().getEventCode())
                .generatedAt(pass.getGeneratedAt())
                .activatedAt(pass.getActivatedAt())
                .attendeeName(attendeeName)
                .attendeeEmail(attendeeEmail)
                .attendeeMobileNumber(attendeeMobile)
                .checkedIn(isCheckedIn)
                .checkInCount(count)
                .lastCheckedInAt(lastCheckIn)
                .validDays(mapDays(pass.getValidDays()))
                .rawToken(null)
                .build();
    }
}
