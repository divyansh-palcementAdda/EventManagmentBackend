package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.DuplicateResourceException;
import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.payload.event.CreateEventRequest;
import com.app.eventmanagmentsystem.payload.event.EventResponse;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
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

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventPassRepository eventPassRepository;
    private final EventCodeGenerator eventCodeGenerator;
    private final PassSeriesCodeGenerator passSeriesCodeGenerator;
    private final AuditLogService auditLogService;

    @PreAuthorize("hasAuthority('EVENT_CREATE')")
    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        String code = resolveEventCode(request);
        if (eventRepository.existsByEventCode(code)) {
            throw new DuplicateResourceException("Event code already exists: " + code);
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

    @PreAuthorize("hasAuthority('EVENT_VIEW')")
    @Transactional(readOnly = true)
    public Page<EventResponse> listEvents(Pageable pageable) {
        return eventRepository.findAll(pageable).map(this::toResponse);
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

    public EventResponse toResponse(Event event) {
        long totalPasses = eventPassRepository.countByEventId(event.getId());
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
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}

