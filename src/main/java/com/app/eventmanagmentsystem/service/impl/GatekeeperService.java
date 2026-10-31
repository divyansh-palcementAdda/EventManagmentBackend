package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.DuplicateResourceException;
import com.app.eventmanagmentsystem.exceptions.EventAccessDeniedException;
import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventGatekeeper;
import com.app.eventmanagmentsystem.model.Role;
import com.app.eventmanagmentsystem.model.User;
import com.app.eventmanagmentsystem.model.enums.AccountStatus;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.payload.gatekeeper.CreateGatekeeperRequest;
import com.app.eventmanagmentsystem.payload.gatekeeper.GatekeeperResponse;
import com.app.eventmanagmentsystem.repository.EventGatekeeperRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.repository.RoleRepository;
import com.app.eventmanagmentsystem.repository.UserRepository;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatekeeperService {

    private final EventGatekeeperRepository gatekeeperRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @PreAuthorize("hasAuthority('EVENT_VIEW') or hasAuthority('GATEKEEPER_ASSIGN')")
    @Transactional(readOnly = true)
    public List<GatekeeperResponse> getEventGatekeepers(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", eventId.toString());
        }

        List<EventGatekeeper> assignments = gatekeeperRepository.findAllAssignmentsByEvent(eventId);
        return assignments.stream().map(eg -> {
            User u = eg.getUser();
            List<String> otherEvents = gatekeeperRepository.findActiveEventsByGatekeeper(u.getId())
                    .stream()
                    .filter(otherEg -> !otherEg.getEvent().getId().equals(eventId))
                    .map(otherEg -> otherEg.getEvent().getEventCode())
                    .collect(Collectors.toList());

            return GatekeeperResponse.builder()
                    .assignmentId(eg.getId())
                    .eventId(eventId)
                    .userId(u.getId())
                    .fullName(u.getFullName())
                    .email(u.getEmail())
                    .mobileNumber(u.getMobileNumber())
                    .accountStatus(u.getAccountStatus() != null ? u.getAccountStatus().name() : "ACTIVE")
                    .active(eg.isActive())
                    .assignedAt(eg.getAssignedAt())
                    .assignedByName(eg.getAssignedByName())
                    .alreadyAssigned(eg.isActive())
                    .otherAssignedEvents(otherEvents)
                    .build();
        }).collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('GATEKEEPER_ASSIGN')")
    @Transactional(readOnly = true)
    public Page<GatekeeperResponse> searchEligibleGatekeepers(UUID eventId, String query, Pageable pageable) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", eventId.toString());
        }

        Set<UUID> activeGatekeeperUserIds = gatekeeperRepository.findActiveGatekeeperUserIdsByEvent(eventId);
        Page<User> usersPage = userRepository.searchGatekeeperUsers(query, pageable);

        return usersPage.map(u -> {
            boolean alreadyAssigned = activeGatekeeperUserIds.contains(u.getId());
            List<String> otherEvents = gatekeeperRepository.findActiveEventsByGatekeeper(u.getId())
                    .stream()
                    .filter(otherEg -> !otherEg.getEvent().getId().equals(eventId))
                    .map(otherEg -> otherEg.getEvent().getEventCode())
                    .collect(Collectors.toList());

            return GatekeeperResponse.builder()
                    .eventId(eventId)
                    .userId(u.getId())
                    .fullName(u.getFullName())
                    .email(u.getEmail())
                    .mobileNumber(u.getMobileNumber())
                    .accountStatus(u.getAccountStatus() != null ? u.getAccountStatus().name() : "ACTIVE")
                    .active(alreadyAssigned)
                    .alreadyAssigned(alreadyAssigned)
                    .otherAssignedEvents(otherEvents)
                    .build();
        });
    }

    @PreAuthorize("hasAuthority('GATEKEEPER_ASSIGN')")
    @Transactional
    public GatekeeperResponse createAndAssignGatekeeper(UUID eventId, CreateGatekeeperRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new InvalidRequestException("Cannot assign gatekeepers to a cancelled event.");
        }

        String normalizedEmail = request.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException(
                    "A user with email '" + normalizedEmail
                            + "' already exists. Please use 'Add Existing Gatekeeper' to assign them.");
        }

        Role gatekeeperRole = roleRepository.findByName("GATEKEEPER")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName("GATEKEEPER");
                    r.setDescription("Event gatekeeper role");
                    return roleRepository.save(r);
                });

        String plainPassword = request.getPassword() != null && !request.getPassword().isBlank()
                ? request.getPassword()
                : generateSecurePassword();

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setFullName(request.getFullName().trim());
        user.setMobileNumber(request.getMobileNumber());
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setRoles(new HashSet<>(Set.of(gatekeeperRole)));
        User savedUser = userRepository.save(user);

        AppUserDetails actor = currentUser();

        EventGatekeeper assignment = new EventGatekeeper();
        assignment.setEvent(event);
        assignment.setUser(savedUser);
        assignment.setAssignedAt(Instant.now());
        assignment.setAssignedById(actor.getUserId().toString());
        assignment.setAssignedByName(actor.getFullName());
        assignment.setActive(true);
        EventGatekeeper savedAssignment = gatekeeperRepository.save(assignment);

        auditLogService.log("GATEKEEPER_CREATE_AND_ASSIGN", "EventGatekeeper", savedAssignment.getId().toString(),
                eventId.toString(), AuditOutcome.SUCCESS, null,
                "New user " + normalizedEmail + " created and assigned as gatekeeper for event "
                        + event.getEventCode());

        return GatekeeperResponse.builder()
                .assignmentId(savedAssignment.getId())
                .eventId(eventId)
                .userId(savedUser.getId())
                .fullName(savedUser.getFullName())
                .email(savedUser.getEmail())
                .mobileNumber(savedUser.getMobileNumber())
                .accountStatus(savedUser.getAccountStatus().name())
                .active(true)
                .assignedAt(savedAssignment.getAssignedAt())
                .assignedByName(savedAssignment.getAssignedByName())
                .alreadyAssigned(true)
                .otherAssignedEvents(Collections.emptyList())
                .build();
    }

    @PreAuthorize("hasAuthority('GATEKEEPER_ASSIGN')")
    @Transactional
    public void assignGatekeeper(UUID eventId, UUID userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new InvalidRequestException("Cannot assign gatekeepers to a cancelled event.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        boolean hasGatekeeperRole = user.getRoles().stream()
                .anyMatch(r -> "GATEKEEPER".equals(r.getName()) || "SUPER_ADMIN".equals(r.getName())
                        || "EVENT_ADMIN".equals(r.getName()));
        if (!hasGatekeeperRole) {
            Role gatekeeperRole = roleRepository.findByName("GATEKEEPER")
                    .orElseThrow(() -> new ResourceNotFoundException("Role", "GATEKEEPER"));
            user.getRoles().add(gatekeeperRole);
            userRepository.save(user);
        }

        Optional<EventGatekeeper> existingActive = gatekeeperRepository.findActiveAssignment(eventId, userId);
        if (existingActive.isPresent()) {
            throw new DuplicateResourceException("User is already an active gatekeeper for this event.");
        }

        AppUserDetails actor = currentUser();

        EventGatekeeper assignment = new EventGatekeeper();
        assignment.setEvent(event);
        assignment.setUser(user);
        assignment.setAssignedAt(Instant.now());
        assignment.setAssignedById(actor.getUserId().toString());
        assignment.setAssignedByName(actor.getFullName());
        assignment.setActive(true);

        gatekeeperRepository.save(assignment);
        auditLogService.log("GATEKEEPER_ASSIGN", "EventGatekeeper", null,
                eventId.toString(), AuditOutcome.SUCCESS, null,
                "Gatekeeper assigned: " + userId + " to event " + event.getEventCode());
    }

    @PreAuthorize("hasAuthority('GATEKEEPER_REMOVE')")
    @Transactional
    public void removeGatekeeper(UUID eventId, UUID userId) {
        EventGatekeeper assignment = gatekeeperRepository.findActiveAssignment(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Active gatekeeper assignment",
                        "event=" + eventId + ",user=" + userId));

        AppUserDetails actor = currentUser();
        assignment.setActive(false);
        assignment.setDeactivatedAt(Instant.now());
        assignment.setDeactivatedById(actor.getUserId().toString());
        gatekeeperRepository.save(assignment);

        auditLogService.log("GATEKEEPER_REMOVE", "EventGatekeeper", assignment.getId().toString(),
                eventId.toString(), AuditOutcome.SUCCESS, null,
                "Gatekeeper removed: " + userId + " from event " + eventId);
    }

    private AppUserDetails currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserDetails ud) {
            return ud;
        }
        throw new EventAccessDeniedException();
    }

    private String generateSecurePassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
