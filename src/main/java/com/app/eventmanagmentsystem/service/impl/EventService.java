package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.DuplicateResourceException;
import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventDay;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.event.CreateEventRequest;
import com.app.eventmanagmentsystem.payload.event.DashboardStatsResponse;
import com.app.eventmanagmentsystem.payload.event.EventDayResponse;
import com.app.eventmanagmentsystem.payload.event.EventResponse;
import com.app.eventmanagmentsystem.repository.*;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import com.app.eventmanagmentsystem.util.EventCodeGenerator;
import com.app.eventmanagmentsystem.util.PassSeriesCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventDayRepository eventDayRepository;
    private final EventPassRepository eventPassRepository;
    private final AttendeeRepository attendeeRepository;
    private final CheckInRepository checkInRepository;
    private final EventCodeGenerator eventCodeGenerator;
    private final PassSeriesCodeGenerator passSeriesCodeGenerator;
    private final AuditLogService auditLogService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    @PreAuthorize("hasAuthority('EVENT_CREATE')")
    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        String code = resolveEventCode(request);
        if (eventRepository.existsByEventCode(code)) {
            throw new DuplicateResourceException("Event code already exists: " + code);
        }

        if (request.getStartsAt() != null && request.getEndsAt() != null 
                && request.getEndsAt().isBefore(request.getStartsAt())) {
            throw new InvalidRequestException("Event end date and time must be after start date and time.");
        }

        if (request.getActivationStartsAt() != null && request.getActivationEndsAt() != null 
                && request.getActivationEndsAt().isBefore(request.getActivationStartsAt())) {
            throw new InvalidRequestException("Activation window end date must be after activation start date.");
        }

        Event event = new Event();
        event.setName(request.getName());
        event.setDescription(request.getDescription());
        event.setEventCode(code);
        event.setPassSeriesCode(passSeriesCodeGenerator.generate());
        event.setVenue(request.getVenue());
        event.setStartsAt(request.getStartsAt());
        event.setEndsAt(request.getEndsAt());
        event.setTimezone(request.getTimezone() != null ? request.getTimezone() : "UTC");
        event.setActivationPolicy(request.getActivationPolicy());
        event.setCheckInPolicy(request.getCheckInPolicy());
        event.setMaximumPasses(request.getMaximumPasses());
        event.setActivationStartsAt(request.getActivationStartsAt());
        event.setActivationEndsAt(request.getActivationEndsAt());
        event.setStatus(EventStatus.DRAFT);

        Event saved = eventRepository.save(event);

        // Derive and generate event days
        generateEventDays(saved);

        auditLogService.log("EVENT_CREATE", "Event", saved.getId().toString(),
                saved.getId().toString(), AuditOutcome.SUCCESS, null, "Event created: " + code);
        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('EVENT_VIEW')")
    @Transactional(readOnly = true)
    public EventResponse getEvent(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
        return toResponse(event);
    }

    @PreAuthorize("hasAuthority('EVENT_UPDATE')")
    @Transactional
    public EventResponse updateEvent(UUID eventId, CreateEventRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));

        if (request.getEventCode() != null && !request.getEventCode().isBlank()) {
            String code = request.getEventCode().toUpperCase();
            if (!code.equals(event.getEventCode()) && eventRepository.existsByEventCode(code)) {
                throw new DuplicateResourceException("Event code already exists: " + code);
            }
            if (!eventCodeGenerator.isValidCode(code)) {
                throw new InvalidRequestException("Invalid event code format: " + code);
            }
            event.setEventCode(code);
        }

        if (request.getStartsAt() != null && request.getEndsAt() != null 
                && request.getEndsAt().isBefore(request.getStartsAt())) {
            throw new InvalidRequestException("Event end date and time must be after start date and time.");
        }

        if (request.getActivationStartsAt() != null && request.getActivationEndsAt() != null 
                && request.getActivationEndsAt().isBefore(request.getActivationStartsAt())) {
            throw new InvalidRequestException("Activation window end date must be after activation start date.");
        }

        event.setName(request.getName());
        event.setDescription(request.getDescription());
        event.setVenue(request.getVenue());
        event.setStartsAt(request.getStartsAt());
        event.setEndsAt(request.getEndsAt());
        if (request.getTimezone() != null && !request.getTimezone().isBlank()) {
            event.setTimezone(request.getTimezone());
        }
        if (request.getActivationPolicy() != null) {
            event.setActivationPolicy(request.getActivationPolicy());
        }
        if (request.getCheckInPolicy() != null) {
            event.setCheckInPolicy(request.getCheckInPolicy());
        }
        event.setMaximumPasses(request.getMaximumPasses());
        event.setActivationStartsAt(request.getActivationStartsAt());
        event.setActivationEndsAt(request.getActivationEndsAt());

        Event saved = eventRepository.save(event);

        auditLogService.log("EVENT_UPDATE", "Event", saved.getId().toString(),
                saved.getId().toString(), AuditOutcome.SUCCESS, null, "Event updated: " + saved.getEventCode());
        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('EVENT_VIEW')")
    @Transactional(readOnly = true)
    public Page<EventResponse> listEvents(Pageable pageable) {
        return eventRepository.findAll(pageable).map(this::toResponse);
    }

    @PreAuthorize("hasAuthority('EVENT_VIEW')")
    @Transactional(readOnly = true)
    public List<EventDayResponse> getEventDays(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", eventId.toString());
        }
        return eventDayRepository.findByEventIdOrderByDayNumberAsc(eventId)
                .stream()
                .map(this::toDayResponse)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('EVENT_UPDATE')")
    @Transactional
    public EventResponse updateEventStatus(UUID eventId, EventStatus newStatus) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
        event.setStatus(newStatus);
        Event saved = eventRepository.save(event);
        auditLogService.log("EVENT_STATUS_CHANGE", "Event", eventId.toString(),
                eventId.toString(), AuditOutcome.SUCCESS, null, "Status changed to: " + newStatus);
        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('EVENT_CANCEL')")
    @Transactional
    public void deleteEvent(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));
        event.setStatus(EventStatus.CANCELLED);
        eventRepository.save(event);
        auditLogService.log("EVENT_CANCEL", "Event", eventId.toString(),
                eventId.toString(), AuditOutcome.SUCCESS, null, "Event cancelled: " + event.getEventCode());
    }

    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats() {
        long totalEvents = eventRepository.count();
        long activeEvents = eventRepository.findAll().stream()
                .filter(e -> e.getStatus() == EventStatus.ACTIVE || e.getStatus() == EventStatus.PUBLISHED)
                .count();
        long upcomingEvents = eventRepository.findAll().stream()
                .filter(e -> e.getStatus() == EventStatus.DRAFT)
                .count();
        long totalPassesGenerated = eventPassRepository.count();
        long totalPassesActivated = eventPassRepository.findAll().stream()
                .filter(p -> p.getStatus() == PassStatus.ACTIVATED)
                .count();
        long totalAttendees = attendeeRepository.count();
        long totalCheckIns = checkInRepository.count();

        return DashboardStatsResponse.builder()
                .totalEvents(totalEvents)
                .activeEvents(activeEvents)
                .upcomingEvents(upcomingEvents)
                .totalPassesGenerated(totalPassesGenerated)
                .totalPassesActivated(totalPassesActivated)
                .totalAttendees(totalAttendees)
                .totalCheckIns(totalCheckIns)
                .build();
    }

    private void generateEventDays(Event event) {
        if (eventDayRepository.existsByEventId(event.getId())) {
            return;
        }

        ZoneId zone = ZoneId.of("UTC");
        try {
            if (event.getTimezone() != null && !event.getTimezone().isBlank()) {
                zone = ZoneId.of(event.getTimezone());
            }
        } catch (Exception ignored) {}

        LocalDate startDate = event.getStartsAt() != null
                ? event.getStartsAt().atZone(zone).toLocalDate()
                : LocalDate.now(zone);
        LocalDate endDate = event.getEndsAt() != null
                ? event.getEndsAt().atZone(zone).toLocalDate()
                : startDate;

        if (endDate.isBefore(startDate)) {
            endDate = startDate;
        }

        List<EventDay> days = new ArrayList<>();
        int dayNum = 1;
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            String label = String.format("Day %d — %s", dayNum, current.format(DATE_FORMATTER));
            EventDay day = new EventDay(event, dayNum, current, label);
            days.add(day);
            dayNum++;
            current = current.plusDays(1);
        }

        eventDayRepository.saveAll(days);
    }

    private String resolveEventCode(CreateEventRequest request) {
        if (request.getEventCode() != null && !request.getEventCode().isBlank()) {
            String code = request.getEventCode().toUpperCase();
            if (!eventCodeGenerator.isValidCode(code)) {
                throw new InvalidRequestException("Invalid event code format: " + code);
            }
            return code;
        }
        return eventCodeGenerator.generateFromName(request.getName());
    }

    public EventDayResponse toDayResponse(EventDay day) {
        return EventDayResponse.builder()
                .id(day.getId())
                .dayNumber(day.getDayNumber())
                .eventDate(day.getEventDate())
                .label(day.getLabel())
                .build();
    }

    public EventResponse toResponse(Event event) {
        long totalPasses = eventPassRepository.countByEventId(event.getId());
        List<EventDayResponse> days = eventDayRepository.findByEventIdOrderByDayNumberAsc(event.getId())
                .stream()
                .map(this::toDayResponse)
                .collect(Collectors.toList());

        return EventResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .description(event.getDescription())
                .eventCode(event.getEventCode())
                .passSeriesCode(event.getPassSeriesCode())
                .venue(event.getVenue())
                .startsAt(event.getStartsAt())
                .endsAt(event.getEndsAt())
                .timezone(event.getTimezone())
                .status(event.getStatus())
                .activationPolicy(event.getActivationPolicy())
                .checkInPolicy(event.getCheckInPolicy())
                .maximumPasses(event.getMaximumPasses())
                .totalPassesGenerated(totalPasses)
                .days(days)
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
