package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventDay;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.event.EventDayResponse;
import com.app.eventmanagmentsystem.payload.pass.PassResponse;
import com.app.eventmanagmentsystem.payload.pass.UpdatePassValidityRequest;
import com.app.eventmanagmentsystem.payload.pass.UpdatePassValidityResponse;
import com.app.eventmanagmentsystem.repository.CheckInRepository;
import com.app.eventmanagmentsystem.repository.EventDayRepository;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PassValidityService {

    private final EventRepository eventRepository;
    private final EventPassRepository passRepository;
    private final EventDayRepository eventDayRepository;
    private final CheckInRepository checkInRepository;
    private final AuditLogService auditLogService;

    @PreAuthorize("hasAuthority('PASS_GENERATE') or hasAuthority('EVENT_UPDATE')")
    @Transactional
    public UpdatePassValidityResponse updatePassValidity(UUID eventId, UpdatePassValidityRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new InvalidRequestException("Cannot update pass validity for a cancelled event.");
        }

        List<EventDay> allEventDays = eventDayRepository.findByEventIdOrderByDayNumberAsc(eventId);
        if (allEventDays.isEmpty()) {
            throw new InvalidRequestException("No event days configured for event " + event.getEventCode());
        }

        // Determine target permitted days
        Set<EventDay> targetDays;
        List<String> dayLabels;
        if (request.getValidDayIds() == null || request.getValidDayIds().isEmpty()) {
            targetDays = new HashSet<>(allEventDays);
            dayLabels = List.of("All Days (" + allEventDays.size() + " days)");
        } else {
            Set<UUID> validDayIdsSet = new HashSet<>(request.getValidDayIds());
            targetDays = allEventDays.stream()
                    .filter(d -> validDayIdsSet.contains(d.getId()))
                    .collect(Collectors.toSet());

            if (targetDays.size() != validDayIdsSet.size()) {
                throw new InvalidRequestException("One or more selected event days do not belong to event " + event.getEventCode());
            }

            dayLabels = targetDays.stream()
                    .sorted(Comparator.comparingInt(EventDay::getDayNumber))
                    .map(EventDay::getLabel)
                    .collect(Collectors.toList());
        }

        // Resolve target passes
        List<EventPass> targetPasses;
        if (request.getPassIds() != null && !request.getPassIds().isEmpty()) {
            targetPasses = passRepository.findByEventIdAndIdIn(eventId, request.getPassIds());
        } else if (request.getFromSequence() != null && request.getToSequence() != null) {
            if (request.getFromSequence() > request.getToSequence()) {
                throw new InvalidRequestException("fromSequence cannot be greater than toSequence.");
            }
            targetPasses = passRepository.findByEventIdAndSequenceNumberBetweenOrderBySequenceNumberAsc(
                    eventId, request.getFromSequence(), request.getToSequence());
        } else if (Boolean.TRUE.equals(request.getAllEligible())) {
            targetPasses = passRepository.findByEventIdOrderBySequenceNumberAsc(eventId);
        } else {
            throw new InvalidRequestException("Please specify passes via passIds, numeric sequence range, or allEligible.");
        }

        if (targetPasses.isEmpty()) {
            throw new InvalidRequestException("No passes found matching the specified selection.");
        }

        // Update validity while preserving attendee, activation status, and pass identity
        int updatedCount = 0;
        int skippedCount = 0;

        for (EventPass pass : targetPasses) {
            if (pass.getStatus() == PassStatus.REVOKED) {
                skippedCount++;
                continue;
            }

            pass.getValidDays().clear();
            pass.getValidDays().addAll(targetDays);
            passRepository.save(pass);
            updatedCount++;
        }

        auditLogService.log("PASS_VALIDITY_UPDATE", "EventPass", null,
                eventId.toString(), AuditOutcome.SUCCESS, null,
                String.format("Updated validity for %d passes (skipped %d revoked) to %s",
                        updatedCount, skippedCount, String.join(", ", dayLabels)));

        return UpdatePassValidityResponse.builder()
                .eventId(eventId)
                .updatedPassesCount(updatedCount)
                .skippedPassesCount(skippedCount)
                .validDayLabels(dayLabels)
                .updatedAt(Instant.now())
                .message(String.format("Successfully updated validity for %d passes (%d skipped).", updatedCount, skippedCount))
                .build();
    }

    @PreAuthorize("hasAuthority('PASS_GENERATE') or hasAuthority('EVENT_UPDATE')")
    @Transactional
    public PassResponse updateSinglePassValidity(UUID passId, List<UUID> validDayIds) {
        EventPass pass = passRepository.findById(passId)
                .orElseThrow(() -> new ResourceNotFoundException("EventPass", passId.toString()));

        if (pass.getStatus() == PassStatus.REVOKED) {
            throw new InvalidRequestException("Cannot modify validity of a revoked pass.");
        }

        Event event = pass.getEvent();
        List<EventDay> allDays = eventDayRepository.findByEventIdOrderByDayNumberAsc(event.getId());

        Set<EventDay> targetDays;
        if (validDayIds == null || validDayIds.isEmpty()) {
            targetDays = new HashSet<>(allDays);
        } else {
            Set<UUID> validDayIdsSet = new HashSet<>(validDayIds);
            targetDays = allDays.stream()
                    .filter(d -> validDayIdsSet.contains(d.getId()))
                    .collect(Collectors.toSet());

            if (targetDays.size() != validDayIdsSet.size()) {
                throw new InvalidRequestException("One or more selected event days do not belong to event " + event.getEventCode());
            }
        }

        pass.getValidDays().clear();
        pass.getValidDays().addAll(targetDays);
        EventPass saved = passRepository.save(pass);

        auditLogService.log("PASS_VALIDITY_UPDATE", "EventPass", pass.getId().toString(),
                event.getId().toString(), AuditOutcome.SUCCESS, null,
                "Updated validity for pass " + pass.getPassNumber());

        return toResponseWithoutToken(saved);
    }

    private PassResponse toResponseWithoutToken(EventPass pass) {
        String attendeeName = pass.getAttendee() != null ? pass.getAttendee().getFullName() : null;
        String attendeeEmail = pass.getAttendee() != null ? pass.getAttendee().getEmail() : null;
        String attendeeMobile = pass.getAttendee() != null ? pass.getAttendee().getMobileNumber() : null;

        List<EventDayResponse> validDays = pass.getValidDays().stream()
                .sorted(Comparator.comparingInt(EventDay::getDayNumber))
                .map(d -> EventDayResponse.builder()
                        .id(d.getId())
                        .dayNumber(d.getDayNumber())
                        .eventDate(d.getEventDate())
                        .label(d.getLabel())
                        .build())
                .collect(Collectors.toList());

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
                .validDays(validDays)
                .rawToken(null)
                .build();
    }
}

