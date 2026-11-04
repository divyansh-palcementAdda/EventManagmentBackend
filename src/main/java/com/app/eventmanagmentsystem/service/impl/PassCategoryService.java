package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.DuplicateResourceException;
import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.PassCategory;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.model.enums.CategoryScope;
import com.app.eventmanagmentsystem.payload.category.CreatePassCategoryRequest;
import com.app.eventmanagmentsystem.payload.category.PassCategoryResponse;
import com.app.eventmanagmentsystem.payload.category.UpdatePassCategoryRequest;
import com.app.eventmanagmentsystem.repository.EventPassCategoryRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.repository.PassCategoryRepository;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PassCategoryService {

    private final PassCategoryRepository passCategoryRepository;
    private final EventPassCategoryRepository eventPassCategoryRepository;
    private final EventRepository eventRepository;
    private final AuditLogService auditLogService;

    @PreAuthorize("hasAuthority('PASS_CATEGORY_VIEW')")
    @Transactional(readOnly = true)
    public Page<PassCategoryResponse> listCategories(
            CategoryScope scope,
            UUID eventId,
            Boolean active,
            String search,
            Pageable pageable) {

        Specification<PassCategory> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            if (scope != null) {
                predicates.add(cb.equal(root.get("scope"), scope));
            }
            if (eventId != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("scope"), CategoryScope.GLOBAL),
                        cb.equal(root.get("event").get("id"), eventId)
                ));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return passCategoryRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @PreAuthorize("hasAuthority('PASS_CATEGORY_VIEW')")
    @Transactional(readOnly = true)
    public PassCategoryResponse getCategoryById(UUID id) {
        PassCategory category = passCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PassCategory", id.toString()));
        return toResponse(category);
    }

    @PreAuthorize("hasAuthority('PASS_CATEGORY_CREATE')")
    @Transactional
    public PassCategoryResponse createCategory(CreatePassCategoryRequest request) {
        String code = request.getCode().trim().toUpperCase();

        Event event = null;
        if (request.getScope() == CategoryScope.EVENT_SPECIFIC) {
            if (request.getEventId() == null) {
                throw new InvalidRequestException("Event ID is mandatory for EVENT_SPECIFIC category scope.");
            }
            event = eventRepository.findById(request.getEventId())
                    .orElseThrow(() -> new ResourceNotFoundException("Event", request.getEventId().toString()));

            if (passCategoryRepository.existsByScopeAndCodeAndEventId(CategoryScope.EVENT_SPECIFIC, code, event.getId())) {
                throw new DuplicateResourceException("An event-specific category with code '" + code + "' already exists for this event.");
            }
        } else {
            // GLOBAL scope
            if (passCategoryRepository.existsByScopeAndCode(CategoryScope.GLOBAL, code)) {
                throw new DuplicateResourceException("A global category with code '" + code + "' already exists.");
            }
        }

        PassCategory category = new PassCategory();
        category.setName(request.getName().trim());
        category.setCode(code);
        category.setScope(request.getScope());
        category.setEvent(event);
        category.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        category.setActive(true);
        category.setDisplayOrder(request.getDisplayOrder());

        PassCategory saved = passCategoryRepository.save(category);

        auditLogService.log("PASS_CATEGORY_CREATE", "PassCategory", saved.getId().toString(),
                event != null ? event.getId().toString() : null, AuditOutcome.SUCCESS, null,
                "Created pass category: " + saved.getName() + " (" + saved.getCode() + ") [" + saved.getScope() + "]");

        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('PASS_CATEGORY_UPDATE')")
    @Transactional
    public PassCategoryResponse updateCategory(UUID id, UpdatePassCategoryRequest request) {
        PassCategory category = passCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PassCategory", id.toString()));

        category.setName(request.getName().trim());
        category.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        category.setDisplayOrder(request.getDisplayOrder());

        PassCategory saved = passCategoryRepository.save(category);

        auditLogService.log("PASS_CATEGORY_UPDATE", "PassCategory", saved.getId().toString(),
                saved.getEvent() != null ? saved.getEvent().getId().toString() : null, AuditOutcome.SUCCESS, null,
                "Updated pass category: " + saved.getName());

        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('PASS_CATEGORY_DISABLE')")
    @Transactional
    public PassCategoryResponse toggleCategoryStatus(UUID id) {
        PassCategory category = passCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PassCategory", id.toString()));

        category.setActive(!category.isActive());
        PassCategory saved = passCategoryRepository.save(category);

        auditLogService.log("PASS_CATEGORY_STATUS", "PassCategory", saved.getId().toString(),
                saved.getEvent() != null ? saved.getEvent().getId().toString() : null, AuditOutcome.SUCCESS, null,
                "Toggled category active state to: " + saved.isActive());

        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('PASS_CATEGORY_VIEW')")
    @Transactional(readOnly = true)
    public List<PassCategoryResponse> getAvailableForEvent(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", eventId.toString());
        }
        return passCategoryRepository.findAvailableForEvent(eventId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public PassCategoryResponse toResponse(PassCategory c) {
        long passesCount = passCategoryRepository.countPassesReferencingCategory(c.getId());
        return PassCategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .code(c.getCode())
                .scope(c.getScope())
                .eventId(c.getEvent() != null ? c.getEvent().getId() : null)
                .eventName(c.getEvent() != null ? c.getEvent().getName() : null)
                .description(c.getDescription())
                .active(c.isActive())
                .displayOrder(c.getDisplayOrder())
                .totalPasses(passesCount)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
