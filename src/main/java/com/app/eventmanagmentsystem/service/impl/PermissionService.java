package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.DuplicateResourceException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.Permission;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.payload.permission.CreatePermissionRequest;
import com.app.eventmanagmentsystem.payload.permission.PermissionResponse;
import com.app.eventmanagmentsystem.payload.permission.UpdatePermissionRequest;
import com.app.eventmanagmentsystem.repository.PermissionRepository;
import com.app.eventmanagmentsystem.repository.specification.PermissionSpecification;
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

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final AuditLogService auditLogService;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "resource", "action", "createdAt");

    @PreAuthorize("hasAuthority('PERMISSION_VIEW')")
    @Transactional(readOnly = true)
    public Page<PermissionResponse> getPermissions(String search, String resource, Pageable pageable) {
        Pageable safePageable = sanitizePageable(pageable);
        Specification<Permission> spec = PermissionSpecification.filterPermissions(search, resource);
        Page<Permission> page = permissionRepository.findAll(spec, safePageable);

        return page.map(this::toResponse);
    }

    @PreAuthorize("hasAuthority('PERMISSION_VIEW')")
    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(Permission::getName))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('PERMISSION_VIEW')")
    @Transactional(readOnly = true)
    public List<String> getDistinctResources() {
        return permissionRepository.findDistinctResources();
    }

    @PreAuthorize("hasAuthority('PERMISSION_VIEW')")
    @Transactional(readOnly = true)
    public PermissionResponse getPermissionById(UUID id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", id.toString()));
        return toResponse(permission);
    }

    @PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request) {
        String normalizedName = request.getName().trim().toUpperCase();
        if (permissionRepository.existsByName(normalizedName)) {
            throw new DuplicateResourceException("Permission with name '" + normalizedName + "' already exists.");
        }

        Permission permission = new Permission(
                normalizedName,
                request.getDescription(),
                request.getResource().trim().toUpperCase(),
                request.getAction().trim().toUpperCase()
        );

        Permission saved = permissionRepository.save(permission);

        auditLogService.log("PERMISSION_CREATE", "Permission", saved.getId().toString(),
                saved.getName(), AuditOutcome.SUCCESS, null, "Created permission " + saved.getName());

        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('PERMISSION_MANAGE')")
    @Transactional
    public PermissionResponse updatePermission(UUID id, UpdatePermissionRequest request) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", id.toString()));

        if (request.getDescription() != null) {
            permission.setDescription(request.getDescription().trim());
        }
        if (request.getResource() != null && !request.getResource().isBlank()) {
            permission.setResource(request.getResource().trim().toUpperCase());
        }
        if (request.getAction() != null && !request.getAction().isBlank()) {
            permission.setAction(request.getAction().trim().toUpperCase());
        }

        Permission updated = permissionRepository.save(permission);

        auditLogService.log("PERMISSION_UPDATE", "Permission", id.toString(),
                updated.getName(), AuditOutcome.SUCCESS, null, "Updated permission metadata");

        return toResponse(updated);
    }

    public PermissionResponse toResponse(Permission p) {
        return PermissionResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .resource(p.getResource())
                .action(p.getAction())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
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
            safeOrders.add(Sort.Order.asc("name"));
        }

        int pageSize = Math.min(pageable.getPageSize(), 100);
        return PageRequest.of(pageable.getPageNumber(), pageSize, Sort.by(safeOrders));
    }
}

