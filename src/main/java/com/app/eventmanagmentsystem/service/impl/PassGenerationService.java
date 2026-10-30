package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.*;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.pass.GeneratePassRequest;
import com.app.eventmanagmentsystem.payload.pass.PassResponse;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import com.app.eventmanagmentsystem.util.QrTokenUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    private static final int MAX_UNIQUE_RETRIES = 3;

    private final EventRepository eventRepository;
    private final EventPassRepository passRepository;
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

        List<PassResponse> results = new ArrayList<>(request.getBatchSize());
        for (int i = 0; i < request.getBatchSize(); i++) {
            PassResponse passResponse = generateSinglePass(event, request.getIdempotencyKey(), i);
            results.add(passResponse);
        }

        auditLogService.log("PASS_GENERATE", "EventPass", null,
                event.getId().toString(), AuditOutcome.SUCCESS, null,
                "Generated " + request.getBatchSize() + " passes for event " + event.getEventCode());
        return results;
    }

    private PassResponse generateSinglePass(Event event, String idempotencyKey, int batchIndex) {
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
        pass.setTokenHash(tokenHash);
        pass.setStatus(PassStatus.NOT_ACTIVATED);
        pass.setGeneratedAt(Instant.now());
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
        // Minimum 4 digits, expands naturally beyond 9999
        return String.format("%s-%s-%04d", eventCode, seriesCode, sequence);
    }

    @PreAuthorize("hasAuthority('PASS_VIEW')")
    @Transactional(readOnly = true)
    public Page<PassResponse> listPassesByEvent(UUID eventId, Pageable pageable) {
        return passRepository.findByEventId(eventId, pageable)
                .map(this::toResponseWithoutToken);
    }

    @PreAuthorize("hasAuthority('PASS_VIEW')")
    @Transactional(readOnly = true)
    public PassResponse getPass(UUID passId) {
        EventPass pass = passRepository.findById(passId)
                .orElseThrow(() -> new ResourceNotFoundException("EventPass", passId.toString()));
        return toResponseWithoutToken(pass);
    }

    private PassResponse toResponseWithToken(EventPass pass, String rawToken, Event event) {
        return PassResponse.builder()
                .id(pass.getId())
                .passNumber(pass.getPassNumber())
                .status(pass.getStatus())
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .generatedAt(pass.getGeneratedAt())
                .activatedAt(pass.getActivatedAt())
                .rawToken(rawToken)
                .build();
    }

    private PassResponse toResponseWithoutToken(EventPass pass) {
        return PassResponse.builder()
                .id(pass.getId())
                .passNumber(pass.getPassNumber())
                .status(pass.getStatus())
                .eventId(pass.getEvent().getId())
                .eventCode(pass.getEvent().getEventCode())
                .generatedAt(pass.getGeneratedAt())
                .activatedAt(pass.getActivatedAt())
                .rawToken(null)
                .build();
    }
}

