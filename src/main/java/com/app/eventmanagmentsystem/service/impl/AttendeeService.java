package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.DuplicateResourceException;
import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.Attendee;
import com.app.eventmanagmentsystem.model.CheckIn;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.model.enums.RegistrationStatus;
import com.app.eventmanagmentsystem.payload.attendee.*;
import com.app.eventmanagmentsystem.repository.AttendeeRepository;
import com.app.eventmanagmentsystem.repository.CheckInRepository;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.repository.specification.AttendeeSpecification;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendeeService {

    private final AttendeeRepository attendeeRepository;
    private final EventRepository eventRepository;
    private final EventPassRepository passRepository;
    private final CheckInRepository checkInRepository;
    private final AuditLogService auditLogService;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "fullName", "email", "registeredAt", "createdAt", "organization", "registrationStatus");

    @PreAuthorize("hasAuthority('ATTENDEE_VIEW')")
    @Transactional(readOnly = true)
    public Page<AttendeeResponse> getAttendees(
            UUID eventId,
            String search,
            RegistrationStatus status,
            String checkInStatus,
            Pageable pageable) {
        return getAttendees(eventId, search, status, checkInStatus, null, pageable);
    }

    @PreAuthorize("hasAuthority('ATTENDEE_VIEW')")
    @Transactional(readOnly = true)
    public Page<AttendeeResponse> getAttendees(
            UUID eventId,
            String search,
            RegistrationStatus status,
            String checkInStatus,
            Boolean hasPass,
            Pageable pageable) {

        Pageable safePageable = sanitizePageable(pageable);

        Set<UUID> checkedInAttendeeIds = null;
        if ("CHECKED_IN".equalsIgnoreCase(checkInStatus) || "NOT_CHECKED_IN".equalsIgnoreCase(checkInStatus)) {
            // Find pass IDs that are checked in, then get their attendee IDs
            Set<UUID> checkedInPassIds = eventId != null
                    ? checkInRepository.findCheckedInPassIdsByEventId(eventId)
                    : Collections.emptySet();

            if (!checkedInPassIds.isEmpty()) {
                checkedInAttendeeIds = passRepository.findAllById(checkedInPassIds).stream()
                        .map(p -> p.getAttendee() != null ? p.getAttendee().getId() : null)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
            } else {
                checkedInAttendeeIds = Collections.emptySet();
            }
        }

        Specification<Attendee> spec = AttendeeSpecification.filterAttendees(
                eventId, search, status, checkInStatus, checkedInAttendeeIds, hasPass);

        Page<Attendee> attendeesPage = attendeeRepository.findAll(spec, safePageable);

        return enrichAttendeePage(attendeesPage);
    }

    @PreAuthorize("hasAuthority('ATTENDEE_VIEW')")
    @Transactional(readOnly = true)
    public AttendeeResponse getAttendeeById(UUID id) {
        Attendee attendee = attendeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendee", id.toString()));

        Optional<EventPass> passOpt = passRepository.findByAttendeeId(id);
        List<CheckIn> checkIns = passOpt.map(p -> checkInRepository.findByPassId(p.getId()))
                .orElse(Collections.emptyList());

        return toResponse(attendee, passOpt.orElse(null), checkIns);
    }

    @PreAuthorize("hasAuthority('ATTENDEE_CREATE') or hasAuthority('EVENT_UPDATE')")
    @Transactional
    public AttendeeResponse createAttendee(CreateAttendeeRequest request) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event", request.getEventId().toString()));

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new InvalidRequestException("Cannot register attendees for a cancelled event.");
        }

        String normalizedEmail = request.getEmail().toLowerCase().trim();
        if (attendeeRepository.existsByEventAndEmail(event.getId(), normalizedEmail)) {
            throw new DuplicateResourceException(
                    "An attendee with email '" + normalizedEmail + "' is already registered for event " + event.getEventCode());
        }

        Attendee attendee = new Attendee();
        attendee.setEvent(event);
        attendee.setFullName(request.getFullName().trim());
        attendee.setEmail(normalizedEmail);
        attendee.setMobileNumber(request.getMobileNumber() != null ? request.getMobileNumber().trim() : null);
        attendee.setOrganization(request.getOrganization() != null ? request.getOrganization().trim() : null);
        attendee.setDesignation(request.getDesignation() != null ? request.getDesignation().trim() : null);
        attendee.setRegistrationStatus(RegistrationStatus.CONFIRMED);
        attendee.setRegisteredAt(Instant.now());

        Attendee saved = attendeeRepository.save(attendee);

        auditLogService.log("ATTENDEE_REGISTER", "Attendee", saved.getId().toString(),
                event.getId().toString(), AuditOutcome.SUCCESS, null,
                "Registered attendee " + saved.getFullName() + " for event " + event.getEventCode());

        return toResponse(saved, null, Collections.emptyList());
    }

    @PreAuthorize("hasAuthority('ATTENDEE_UPDATE')")
    @Transactional
    public AttendeeResponse updateAttendee(UUID id, UpdateAttendeeRequest request) {
        Attendee attendee = attendeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendee", id.toString()));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            attendee.setFullName(request.getFullName().trim());
        }
        if (request.getMobileNumber() != null) {
            attendee.setMobileNumber(request.getMobileNumber().trim());
        }
        if (request.getOrganization() != null) {
            attendee.setOrganization(request.getOrganization().trim());
        }
        if (request.getDesignation() != null) {
            attendee.setDesignation(request.getDesignation().trim());
        }
        if (request.getRegistrationStatus() != null) {
            attendee.setRegistrationStatus(request.getRegistrationStatus());
        }

        Attendee updated = attendeeRepository.save(attendee);

        auditLogService.log("ATTENDEE_UPDATE", "Attendee", id.toString(),
                attendee.getEvent().getId().toString(), AuditOutcome.SUCCESS, null,
                "Updated details for attendee " + attendee.getEmail());

        Optional<EventPass> passOpt = passRepository.findByAttendeeId(id);
        List<CheckIn> checkIns = passOpt.map(p -> checkInRepository.findByPassId(p.getId()))
                .orElse(Collections.emptyList());

        return toResponse(updated, passOpt.orElse(null), checkIns);
    }

    @PreAuthorize("hasAuthority('ATTENDEE_DELETE') or hasAuthority('EVENT_UPDATE')")
    @Transactional
    public void deleteAttendee(UUID id) {
        Attendee attendee = attendeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendee", id.toString()));

        attendee.setRegistrationStatus(RegistrationStatus.CANCELLED);
        attendeeRepository.save(attendee);

        auditLogService.log("ATTENDEE_CANCEL", "Attendee", id.toString(),
                attendee.getEvent().getId().toString(), AuditOutcome.SUCCESS, null,
                "Cancelled registration for attendee " + attendee.getEmail());
    }

    @PreAuthorize("hasAuthority('ATTENDEE_VIEW')")
    @Transactional(readOnly = true)
    public AttendeeStatsResponse getAttendeeStats(UUID eventId) {
        if (eventId != null && !eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", eventId.toString());
        }

        long total = eventId != null ? attendeeRepository.countByEventId(eventId) : attendeeRepository.count();
        long confirmed = eventId != null ? attendeeRepository.countConfirmedByEventId(eventId) : attendeeRepository.countConfirmed();
        long cancelled = eventId != null ? attendeeRepository.countCancelledByEventId(eventId) : attendeeRepository.countCancelled();
        long checkedIn = eventId != null ? checkInRepository.countUniqueCheckedInPassesByEvent(eventId) : checkInRepository.countUniqueCheckedInPasses();
        long withPass = eventId != null ? passRepository.countWithAttendeeByEventId(eventId) : passRepository.countWithAttendee();

        return AttendeeStatsResponse.builder()
                .eventId(eventId)
                .totalAttendees(total)
                .confirmedAttendees(confirmed)
                .cancelledAttendees(cancelled)
                .checkedInCount(checkedIn)
                .withPass(withPass)
                .build();
    }

    @PreAuthorize("hasAuthority('ATTENDEE_VIEW') or hasAuthority('ATTENDEE_EXPORT')")
    @Transactional(readOnly = true)
    public List<AttendeeResponse> exportAttendees(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", eventId.toString());
        }

        List<Attendee> attendees = attendeeRepository.findAll(
                AttendeeSpecification.filterAttendees(eventId, null, null, null, null),
                Sort.by(Sort.Direction.ASC, "fullName")
        );

        if (attendees.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> attendeeIds = attendees.stream().map(Attendee::getId).collect(Collectors.toList());
        List<EventPass> passes = passRepository.findByAttendeeIdIn(attendeeIds);
        Map<UUID, EventPass> passByAttendeeId = passes.stream()
                .filter(p -> p.getAttendee() != null)
                .collect(Collectors.toMap(p -> p.getAttendee().getId(), Function.identity(), (p1, p2) -> p1));

        List<UUID> passIds = passes.stream().map(EventPass::getId).collect(Collectors.toList());
        Map<UUID, List<CheckIn>> checkInsByPassId = passIds.isEmpty() ? Collections.emptyMap() :
                checkInRepository.findByPassIdIn(passIds).stream()
                        .collect(Collectors.groupingBy(c -> c.getPass().getId()));

        return attendees.stream().map(a -> {
            EventPass pass = passByAttendeeId.get(a.getId());
            List<CheckIn> checkIns = pass != null ? checkInsByPassId.getOrDefault(pass.getId(), Collections.emptyList()) : Collections.emptyList();
            return toResponse(a, pass, checkIns);
        }).collect(Collectors.toList());
    }

    private Page<AttendeeResponse> enrichAttendeePage(Page<Attendee> attendeesPage) {
        List<Attendee> content = attendeesPage.getContent();
        if (content.isEmpty()) {
            return attendeesPage.map(a -> toResponse(a, null, Collections.emptyList()));
        }

        List<UUID> attendeeIds = content.stream().map(Attendee::getId).collect(Collectors.toList());
        List<EventPass> passes = passRepository.findByAttendeeIdIn(attendeeIds);
        Map<UUID, EventPass> passByAttendeeId = passes.stream()
                .filter(p -> p.getAttendee() != null)
                .collect(Collectors.toMap(p -> p.getAttendee().getId(), Function.identity(), (p1, p2) -> p1));

        List<UUID> passIds = passes.stream().map(EventPass::getId).collect(Collectors.toList());
        Map<UUID, List<CheckIn>> checkInsByPassId = passIds.isEmpty() ? Collections.emptyMap() :
                checkInRepository.findByPassIdIn(passIds).stream()
                        .collect(Collectors.groupingBy(c -> c.getPass().getId()));

        return attendeesPage.map(a -> {
            EventPass pass = passByAttendeeId.get(a.getId());
            List<CheckIn> checkIns = pass != null ? checkInsByPassId.getOrDefault(pass.getId(), Collections.emptyList()) : Collections.emptyList();
            return toResponse(a, pass, checkIns);
        });
    }

    public AttendeeResponse toResponse(Attendee a, EventPass pass, List<CheckIn> checkIns) {
        boolean checkedIn = !checkIns.isEmpty();
        int checkInCount = checkIns.size();
        Instant lastCheckedIn = checkIns.stream()
                .map(CheckIn::getCheckedInAt)
                .max(Instant::compareTo)
                .orElse(null);

        List<AttendeeResponse.CheckInSummary> checkInSummaries = checkIns.stream()
                .map(ci -> AttendeeResponse.CheckInSummary.builder()
                        .id(ci.getId())
                        .gateName(ci.getCheckedInByName() != null ? "Gate (" + ci.getCheckedInByName() + ")" : "Main Gate")
                        .operatorName(ci.getCheckedInByName())
                        .checkedInAt(ci.getCheckedInAt())
                        .dayNumber(ci.getEventDay() != null ? ci.getEventDay().getDayNumber() : null)
                        .build())
                .collect(Collectors.toList());

        AttendeeResponse.EventSummary eventSummary = a.getEvent() != null ? AttendeeResponse.EventSummary.builder()
                .id(a.getEvent().getId())
                .eventCode(a.getEvent().getEventCode())
                .name(a.getEvent().getName())
                .startsAt(a.getEvent().getStartsAt())
                .endsAt(a.getEvent().getEndsAt())
                .venue(a.getEvent().getVenue())
                .build() : null;

        AttendeeResponse.LinkedPassSummary passSummary = pass != null ? AttendeeResponse.LinkedPassSummary.builder()
                .id(pass.getId())
                .passNumber(pass.getPassNumber())
                .sequenceNumber(pass.getSequenceNumber())
                .status(pass.getStatus().name())
                .category(pass.getCategory())
                .token(pass.getPassNumber())
                .build() : null;

        return AttendeeResponse.builder()
                .id(a.getId())
                .eventId(a.getEvent().getId())
                .eventCode(a.getEvent().getEventCode())
                .eventName(a.getEvent().getName())
                .fullName(a.getFullName())
                .email(a.getEmail())
                .mobileNumber(a.getMobileNumber())
                .organization(a.getOrganization())
                .designation(a.getDesignation())
                .college(a.getCollege())
                .course(a.getCourse())
                .yearSemester(a.getYearSemester())
                .studentId(a.getStudentId())
                .registrationStatus(a.getRegistrationStatus())
                .registeredAt(a.getRegisteredAt())
                .hasPass(pass != null)
                .passId(pass != null ? pass.getId() : null)
                .passNumber(pass != null ? pass.getPassNumber() : null)
                .sequenceNumber(pass != null ? pass.getSequenceNumber() : null)
                .passStatus(pass != null ? pass.getStatus().name() : null)
                .passCategory(pass != null ? pass.getCategory() : null)
                .checkedIn(checkedIn)
                .checkInStatus(checkedIn ? "CHECKED_IN" : "NOT_CHECKED_IN")
                .checkInCount(checkInCount)
                .lastCheckedInAt(lastCheckedIn)
                .checkedInAt(lastCheckedIn)
                .event(eventSummary)
                .linkedPass(passSummary)
                .checkInHistory(checkInSummaries)
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    private Pageable sanitizePageable(Pageable pageable) {
        Sort sort = pageable.getSort();
        List<Sort.Order> safeOrders = new ArrayList<>();

        for (Sort.Order order : sort) {
            if (ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                safeOrders.add(order);
            }
        }

        if (safeOrders.isEmpty()) {
            safeOrders.add(Sort.Order.desc("registeredAt"));
        }

        int pageSize = Math.min(pageable.getPageSize(), 100);
        return PageRequest.of(pageable.getPageNumber(), pageSize, Sort.by(safeOrders));
    }
}

