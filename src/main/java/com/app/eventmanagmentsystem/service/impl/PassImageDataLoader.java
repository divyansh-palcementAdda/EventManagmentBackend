package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.Attendee;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventDay;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.payload.pass.PassImageSnapshot;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dedicated transactional data loader for PassImageService.
 *
 * Encapsulates the JPA persistence boundary so that all required entities,
 * collections, and relationships are loaded within a single read-only transaction
 * and transformed into a detached, immutable PassImageSnapshot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PassImageDataLoader {

    private final EventPassRepository passRepository;

    @Transactional(readOnly = true)
    public PassImageSnapshot loadSnapshot(UUID passId) {
        EventPass pass = passRepository.findByIdWithEventAndDaysAndAttendee(passId)
                .or(() -> passRepository.findByIdWithEventAndDays(passId))
                .or(() -> passRepository.findById(passId))
                .orElseThrow(() -> new ResourceNotFoundException("EventPass", passId.toString()));

        return createSnapshotFromEntity(pass);
    }

    public PassImageSnapshot createSnapshotFromEntity(EventPass pass) {
        UUID passId = pass.getId();
        String passNumber = pass.getPassNumber();
        Long seq = pass.getSequenceNumber();
        String category = pass.getCategory() != null ? pass.getCategory() : "STUDENT";

        boolean isNoQr = false;
        String catUpper = category.toUpperCase();
        if (catUpper.contains("VIP") || catUpper.contains("NO_QR")) {
            isNoQr = true;
        }

        String attendeeName = null;
        try {
            Attendee attendee = pass.getAttendee();
            if (attendee != null) {
                attendeeName = attendee.getFullName();
            }
        } catch (Exception e) {
            log.debug("Attendee not initialized for pass {}: {}", passId, e.getMessage());
        }

        String eventName = "Event Pass";
        String eventVenue = null;
        String timezone = "UTC";
        java.time.Instant startsAt = null;
        java.time.Instant endsAt = null;

        try {
            Event event = pass.getEvent();
            if (event != null) {
                eventName = event.getName() != null ? event.getName() : "Event Pass";
                eventVenue = event.getVenue();
                timezone = event.getTimezone() != null && !event.getTimezone().isBlank() ? event.getTimezone() : "UTC";
                startsAt = event.getStartsAt();
                endsAt = event.getEndsAt();
            }
        } catch (Exception e) {
            log.debug("Event not initialized for pass {}: {}", passId, e.getMessage());
        }

        List<LocalDate> validDates = new ArrayList<>();
        try {
            Set<EventDay> days = pass.getValidDays();
            if (days != null && !days.isEmpty()) {
                validDates = days.stream()
                        .map(EventDay::getEventDate)
                        .filter(Objects::nonNull)
                        .sorted()
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.debug("Valid days not initialized for pass {}: {}", passId, e.getMessage());
        }

        return PassImageSnapshot.builder()
                .passId(passId)
                .passNumber(passNumber)
                .sequenceNumber(seq)
                .category(category)
                .noQr(isNoQr)
                .attendeeName(attendeeName)
                .eventName(eventName)
                .eventVenue(eventVenue)
                .timezone(timezone)
                .eventStartsAt(startsAt)
                .eventEndsAt(endsAt)
                .validDates(validDates)
                .build();
    }
}
