package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.DuplicateResourceException;
import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventPassCategory;
import com.app.eventmanagmentsystem.model.PassCategory;
import com.app.eventmanagmentsystem.model.RegistrationFormVersion;
import com.app.eventmanagmentsystem.model.enums.ActivationMode;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.model.enums.CategoryScope;
import com.app.eventmanagmentsystem.model.enums.IdentityPolicy;
import com.app.eventmanagmentsystem.payload.category.ConfigureEventCategoryRequest;
import com.app.eventmanagmentsystem.payload.category.EventPassCategoryResponse;
import com.app.eventmanagmentsystem.payload.category.UpdateEventCategoryPolicyRequest;
import com.app.eventmanagmentsystem.repository.EventPassCategoryRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.repository.PassCategoryRepository;
import com.app.eventmanagmentsystem.repository.RegistrationFormVersionRepository;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPassCategoryService {

    private final EventPassCategoryRepository eventPassCategoryRepository;
    private final PassCategoryRepository passCategoryRepository;
    private final EventRepository eventRepository;
    private final RegistrationFormVersionRepository registrationFormVersionRepository;
    private final AuditLogService auditLogService;

    @PreAuthorize("hasAuthority('EVENT_CATEGORY_VIEW')")
    @Transactional(readOnly = true)
    public List<EventPassCategoryResponse> getCategoriesForEvent(UUID eventId, Boolean enabledOnly) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", eventId.toString());
        }

        List<EventPassCategory> list = Boolean.TRUE.equals(enabledOnly)
                ? eventPassCategoryRepository.findByEventIdAndEnabledTrueOrderByDisplayOrderAsc(eventId)
                : eventPassCategoryRepository.findByEventIdOrderByDisplayOrderAsc(eventId);

        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('EVENT_CATEGORY_VIEW')")
    @Transactional(readOnly = true)
    public EventPassCategoryResponse getEventCategoryById(UUID id) {
        EventPassCategory epc = eventPassCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EventPassCategory", id.toString()));
        return toResponse(epc);
    }

    @PreAuthorize("hasAuthority('EVENT_CATEGORY_MANAGE')")
    @Transactional
    public EventPassCategoryResponse configureCategoryForEvent(UUID eventId, ConfigureEventCategoryRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));

        PassCategory category = passCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("PassCategory", request.getCategoryId().toString()));

        // Scope validation: EVENT_SPECIFIC category must belong to THIS event
        if (category.getScope() == CategoryScope.EVENT_SPECIFIC) {
            if (category.getEvent() == null || !category.getEvent().getId().equals(eventId)) {
                throw new InvalidRequestException("An event-specific category cannot be associated with a different event.");
            }
        }

        if (eventPassCategoryRepository.existsByEventIdAndCategoryId(eventId, request.getCategoryId())) {
            throw new DuplicateResourceException("This category is already configured for this event.");
        }

        validateConfigurationRules(request.getActivationMode(), request.getQrRequired(), request.getIdentityPolicy());

        EventPassCategory epc = new EventPassCategory();
        epc.setEvent(event);
        epc.setCategory(category);
        epc.setEnabled(request.isEnabled());
        epc.setActivationMode(request.getActivationMode());

        // Enforce QR policy based on mode
        if (request.getActivationMode() == ActivationMode.NO_QR_WALK_IN) {
            epc.setQrRequired(false);
        } else {
            epc.setQrRequired(request.getQrRequired() == null || request.getQrRequired());
        }

        epc.setIdentityPolicy(request.getIdentityPolicy());
        epc.setDisplayOrder(request.getDisplayOrder());
        epc.setPassLimit(request.getPassLimit());

        EventPassCategory saved = eventPassCategoryRepository.save(epc);

        auditLogService.log("EVENT_CATEGORY_CONFIGURE", "EventPassCategory", saved.getId().toString(),
                eventId.toString(), AuditOutcome.SUCCESS, null,
                "Configured category " + category.getCode() + " for event " + event.getEventCode()
                        + " [Mode: " + saved.getActivationMode() + "]");

        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('EVENT_CATEGORY_MANAGE')")
    @Transactional
    public EventPassCategoryResponse updateCategoryPolicy(UUID id, UpdateEventCategoryPolicyRequest request) {
        EventPassCategory epc = eventPassCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EventPassCategory", id.toString()));

        validateConfigurationRules(request.getActivationMode(), request.getQrRequired(), request.getIdentityPolicy());

        if (request.getEnabled() != null) epc.setEnabled(request.getEnabled());
        epc.setActivationMode(request.getActivationMode());

        if (request.getActivationMode() == ActivationMode.NO_QR_WALK_IN) {
            epc.setQrRequired(false);
        } else {
            epc.setQrRequired(request.getQrRequired() == null || request.getQrRequired());
        }

        epc.setIdentityPolicy(request.getIdentityPolicy());
        if (request.getDisplayOrder() != null) epc.setDisplayOrder(request.getDisplayOrder());
        epc.setPassLimit(request.getPassLimit());

        EventPassCategory saved = eventPassCategoryRepository.save(epc);

        auditLogService.log("EVENT_CATEGORY_UPDATE", "EventPassCategory", saved.getId().toString(),
                saved.getEvent().getId().toString(), AuditOutcome.SUCCESS, null,
                "Updated category policy for " + saved.getCategory().getCode() + " in event " + saved.getEvent().getEventCode());

        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('EVENT_CATEGORY_MANAGE')")
    @Transactional
    public EventPassCategoryResponse toggleCategoryEnabled(UUID id) {
        EventPassCategory epc = eventPassCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EventPassCategory", id.toString()));

        epc.setEnabled(!epc.isEnabled());
        EventPassCategory saved = eventPassCategoryRepository.save(epc);

        auditLogService.log("EVENT_CATEGORY_TOGGLE", "EventPassCategory", saved.getId().toString(),
                saved.getEvent().getId().toString(), AuditOutcome.SUCCESS, null,
                "Toggled category enabled state to: " + saved.isEnabled());

        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('EVENT_CATEGORY_MANAGE')")
    @Transactional
    public EventPassCategoryResponse toggleCategoryActive(UUID id) {
        return toggleCategoryEnabled(id);
    }

    private void validateConfigurationRules(ActivationMode mode, Boolean qrRequired, IdentityPolicy identityPolicy) {
        if (mode == ActivationMode.NO_QR_WALK_IN) {
            if (Boolean.TRUE.equals(qrRequired)) {
                throw new InvalidRequestException("NO_QR_WALK_IN activation mode cannot have QR generation enabled.");
            }
        }
        if (mode == ActivationMode.SCAN_AUTO_ACTIVATE && identityPolicy == IdentityPolicy.NO_IDENTITY) {
            // Valid: pass is auto-activated immediately on scan with no personal details
        }
    }

    public EventPassCategoryResponse toResponse(EventPassCategory epc) {
        long currentPassCount = eventPassCategoryRepository.countPassesReferencingEventCategory(epc.getId());
        Integer publishedVer = registrationFormVersionRepository
                .findTopByEventPassCategoryIdAndPublishedTrueOrderByVersionNumberDesc(epc.getId())
                .map(RegistrationFormVersion::getVersionNumber)
                .orElse(null);

        return EventPassCategoryResponse.builder()
                .id(epc.getId())
                .eventId(epc.getEvent().getId())
                .eventCode(epc.getEvent().getEventCode())
                .eventName(epc.getEvent().getName())
                .categoryId(epc.getCategory().getId())
                .categoryName(epc.getCategory().getName())
                .categoryCode(epc.getCategory().getCode())
                .categoryScope(epc.getCategory().getScope())
                .enabled(epc.isEnabled())
                .activationMode(epc.getActivationMode())
                .qrRequired(epc.isQrRequired())
                .identityPolicy(epc.getIdentityPolicy())
                .displayOrder(epc.getDisplayOrder())
                .passLimit(epc.getPassLimit())
                .currentPassCount((int) currentPassCount)
                .publishedVersionNumber(publishedVer)
                .createdAt(epc.getCreatedAt())
                .updatedAt(epc.getUpdatedAt())
                .build();
    }
}
