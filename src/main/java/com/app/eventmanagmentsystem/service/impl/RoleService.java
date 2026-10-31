package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.DuplicateResourceException;
import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.Permission;
import com.app.eventmanagmentsystem.model.Role;
import com.app.eventmanagmentsystem.model.User;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.payload.permission.PermissionResponse;
import com.app.eventmanagmentsystem.payload.role.AssignPermissionsRequest;
import com.app.eventmanagmentsystem.payload.role.CreateRoleRequest;
import com.app.eventmanagmentsystem.payload.role.RoleResponse;
import com.app.eventmanagmentsystem.payload.role.UpdateRoleRequest;
import com.app.eventmanagmentsystem.payload.user.UserResponse;
import com.app.eventmanagmentsystem.repository.PermissionRepository;
import com.app.eventmanagmentsystem.repository.RoleRepository;
import com.app.eventmanagmentsystem.repository.UserRepository;
import com.app.eventmanagmentsystem.repository.specification.RoleSpecification;
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
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "createdAt", "updatedAt");

    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    @Transactional(readOnly = true)
    public Page<RoleResponse> getRoles(String search, Boolean systemDefined, Pageable pageable) {
        Pageable safePageable = sanitizePageable(pageable);
        Specification<Role> spec = RoleSpecification.filterRoles(search, systemDefined);
        Page<Role> page = roleRepository.findAll(spec, safePageable);

        return page.map(this::toResponse);
    }

    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAllWithPermissions().stream()
                .sorted(Comparator.comparing(Role::getName))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    @Transactional(readOnly = true)
    public RoleResponse getRoleById(UUID id) {
        Role role = roleRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id.toString()));
        return toResponse(role);
    }

    @PreAuthorize("hasAuthority('ROLE_CREATE')")
    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        String normalizedName = request.getName().trim().toUpperCase();
        if (roleRepository.existsByName(normalizedName)) {
            throw new DuplicateResourceException("Role with name '" + normalizedName + "' already exists.");
        }

        Role role = new Role();
        role.setName(normalizedName);
        role.setDescription(request.getDescription());
        role.setSystemDefined(false);

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            List<Permission> permissions = permissionRepository.findAllById(request.getPermissionIds());
            if (permissions.size() != request.getPermissionIds().size()) {
                throw new InvalidRequestException("One or more permission IDs are invalid.");
            }
            role.setPermissions(new HashSet<>(permissions));
        }

        Role saved = roleRepository.save(role);

        auditLogService.log("ROLE_CREATE", "Role", saved.getId().toString(),
                saved.getName(), AuditOutcome.SUCCESS, null, "Role created with "
                        + saved.getPermissions().size() + " permissions");

        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    @Transactional
    public RoleResponse updateRole(UUID id, UpdateRoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id.toString()));

        if (request.getDescription() != null) {
            role.setDescription(request.getDescription().trim());
        }

        if (request.getPermissionIds() != null) {
            List<Permission> permissions = permissionRepository.findAllById(request.getPermissionIds());
            if (permissions.size() != request.getPermissionIds().size()) {
                throw new InvalidRequestException("One or more permission IDs are invalid.");
            }
            role.setPermissions(new HashSet<>(permissions));
        }

        Role updated = roleRepository.save(role);

        auditLogService.log("ROLE_UPDATE", "Role", id.toString(),
                updated.getName(), AuditOutcome.SUCCESS, null, "Role updated");

        return toResponse(updated);
    }

    @PreAuthorize("hasAuthority('ROLE_DELETE')")
    @Transactional
    public void deleteRole(UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id.toString()));

        if (role.isSystemDefined()) {
            throw new InvalidRequestException("System-defined roles cannot be deleted.");
        }

        int assignedUsersCount = userRepository.countUsersByRoleId(id);
        if (assignedUsersCount > 0) {
            throw new InvalidRequestException("Cannot delete role '" + role.getName()
                    + "' because it is currently assigned to " + assignedUsersCount + " user(s). Reassign them first.");
        }

        roleRepository.delete(role);

        auditLogService.log("ROLE_DELETE", "Role", id.toString(),
                role.getName(), AuditOutcome.SUCCESS, null, "Role deleted");
    }

    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    @Transactional
    public RoleResponse assignPermissions(UUID id, AssignPermissionsRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id.toString()));

        List<Permission> permissions = permissionRepository.findAllById(request.getPermissionIds());
        if (permissions.size() != request.getPermissionIds().size()) {
            throw new InvalidRequestException("One or more permission IDs are invalid.");
        }

        role.getPermissions().addAll(permissions);
        Role saved = roleRepository.save(role);

        auditLogService.log("ROLE_PERMISSIONS_ASSIGNED", "Role", id.toString(),
                saved.getName(), AuditOutcome.SUCCESS, null, "Assigned " + permissions.size() + " permissions");

        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    @Transactional
    public RoleResponse replacePermissions(UUID id, AssignPermissionsRequest request) {
        Role role = roleRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id.toString()));

        Set<UUID> requestedIds = request.getPermissionIds() != null ? request.getPermissionIds() : Collections.emptySet();
        List<Permission> permissions = requestedIds.isEmpty()
                ? Collections.emptyList()
                : permissionRepository.findAllById(requestedIds);

        if (permissions.size() != requestedIds.size()) {
            throw new InvalidRequestException("One or more permission IDs are invalid.");
        }

        role.getPermissions().clear();
        role.getPermissions().addAll(permissions);
        Role saved = roleRepository.save(role);

        auditLogService.log("ROLE_PERMISSIONS_REPLACED", "Role", id.toString(),
                saved.getName(), AuditOutcome.SUCCESS, null, "Replaced with " + permissions.size() + " permissions");

        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    @Transactional(readOnly = true)
    public List<PermissionResponse> getRolePermissions(UUID id) {
        Role role = roleRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id.toString()));

        return (role.getPermissions() != null ? role.getPermissions().stream() : java.util.stream.Stream.<Permission>empty())
                .sorted(Comparator.comparing(Permission::getName))
                .map(p -> PermissionResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .description(p.getDescription())
                        .resource(p.getResource())
                        .action(p.getAction())
                        .build())
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    @Transactional
    public RoleResponse removePermission(UUID id, UUID permissionId) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id.toString()));

        boolean removed = role.getPermissions().removeIf(p -> p.getId().equals(permissionId));
        if (!removed) {
            throw new InvalidRequestException("Permission is not assigned to this role.");
        }

        Role saved = roleRepository.save(role);

        auditLogService.log("ROLE_PERMISSION_REMOVED", "Role", id.toString(),
                saved.getName(), AuditOutcome.SUCCESS, null, "Permission removed from role");

        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    @Transactional(readOnly = true)
    public Page<UserResponse> getRoleUsers(UUID id, Pageable pageable) {
        if (!roleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Role", id.toString());
        }

        Page<User> users = userRepository.findUsersByRoleId(id, pageable);
        return users.map(u -> UserResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .mobileNumber(u.getMobileNumber())
                .accountStatus(u.getAccountStatus() != null ? u.getAccountStatus().name() : "ACTIVE")
                .createdAt(u.getCreatedAt())
                .build());
    }

    public RoleResponse toResponse(Role role) {
        int userCount = userRepository.countUsersByRoleId(role.getId());
        List<PermissionResponse> permissionResponses = (role.getPermissions() != null ? role.getPermissions().stream() : java.util.stream.Stream.<Permission>empty())
                .sorted(Comparator.comparing(Permission::getName))
                .map(p -> PermissionResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .description(p.getDescription())
                        .resource(p.getResource())
                        .action(p.getAction())
                        .build())
                .collect(Collectors.toList());

        List<String> permissionNames = permissionResponses.stream()
                .map(PermissionResponse::getName)
                .collect(Collectors.toList());

        List<UUID> permissionIds = permissionResponses.stream()
                .map(PermissionResponse::getId)
                .collect(Collectors.toList());

        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .systemDefined(role.isSystemDefined())
                .userCount(userCount)
                .permissionCount(permissionResponses.size())
                .permissionNames(permissionNames)
                .permissionIds(permissionIds)
                .permissions(permissionResponses)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .createdByName(role.getCreatedByName())
                .updatedByName(role.getUpdatedByName())
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

