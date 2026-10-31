package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.DuplicateResourceException;
import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.Role;
import com.app.eventmanagmentsystem.model.User;
import com.app.eventmanagmentsystem.model.enums.AccountStatus;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.payload.permission.PermissionResponse;
import com.app.eventmanagmentsystem.payload.role.RoleResponse;
import com.app.eventmanagmentsystem.payload.user.*;
import com.app.eventmanagmentsystem.repository.RoleRepository;
import com.app.eventmanagmentsystem.repository.UserRepository;
import com.app.eventmanagmentsystem.repository.specification.UserSpecification;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "fullName", "email", "accountStatus", "updatedAt");

    @PreAuthorize("hasAuthority('USER_VIEW')")
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(
            String search,
            AccountStatus status,
            String roleName,
            Pageable pageable) {

        Pageable safePageable = sanitizePageable(pageable);
        Specification<User> spec = UserSpecification.filterUsers(search, status, roleName);
        Page<User> page = userRepository.findAll(spec, safePageable);

        return page.map(this::toResponse);
    }

    @PreAuthorize("hasAuthority('USER_VIEW')")
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));
        return toResponse(user);
    }

    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("A user with email '" + normalizedEmail + "' already exists.");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setFullName(request.getFullName().trim());
        user.setMobileNumber(request.getMobileNumber());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setAccountStatus(request.getAccountStatus() != null ? request.getAccountStatus() : AccountStatus.ACTIVE);

        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            List<Role> roles = roleRepository.findAllById(request.getRoleIds());
            if (roles.size() != request.getRoleIds().size()) {
                throw new InvalidRequestException("One or more role IDs are invalid.");
            }
            user.setRoles(new HashSet<>(roles));
        }

        User saved = userRepository.save(user);

        auditLogService.log("USER_CREATE", "User", saved.getId().toString(),
                saved.getEmail(), AuditOutcome.SUCCESS, null, "User account created with roles: "
                        + saved.getRoles().stream().map(Role::getName).collect(Collectors.joining(", ")));

        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getMobileNumber() != null) {
            user.setMobileNumber(request.getMobileNumber().trim());
        }
        if (request.getAccountStatus() != null) {
            checkNotSelfStatusChange(user, request.getAccountStatus());
            user.setAccountStatus(request.getAccountStatus());
            if (request.getAccountStatus() != AccountStatus.ACTIVE) {
                refreshTokenService.revokeAllUserTokens(id, "ACCOUNT_STATUS_CHANGED");
            }
        }
        if (request.getRoleIds() != null) {
            List<Role> roles = roleRepository.findAllById(request.getRoleIds());
            if (roles.size() != request.getRoleIds().size()) {
                throw new InvalidRequestException("One or more role IDs are invalid.");
            }
            user.setRoles(new HashSet<>(roles));
            refreshTokenService.revokeAllUserTokens(id, "ROLES_UPDATED");
        }

        User updated = userRepository.save(user);

        auditLogService.log("USER_UPDATE", "User", id.toString(),
                updated.getEmail(), AuditOutcome.SUCCESS, null, "User account updated");

        return toResponse(updated);
    }

    @PreAuthorize("hasAuthority('USER_DISABLE')")
    @Transactional
    public UserResponse updateUserStatus(UUID id, AccountStatus status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));

        checkNotSelfStatusChange(user, status);

        user.setAccountStatus(status);
        if (status != AccountStatus.ACTIVE) {
            refreshTokenService.revokeAllUserTokens(id, "ACCOUNT_DISABLED");
        }
        User saved = userRepository.save(user);

        auditLogService.log("USER_STATUS_CHANGE", "User", id.toString(),
                saved.getEmail(), AuditOutcome.SUCCESS, null, "Account status changed to " + status);

        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    @Transactional
    public UserResponse assignRoles(UUID id, AssignRolesRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));

        List<Role> roles = roleRepository.findAllById(request.getRoleIds());
        if (roles.size() != request.getRoleIds().size()) {
            throw new InvalidRequestException("One or more role IDs are invalid.");
        }

        user.setRoles(new HashSet<>(roles));
        User saved = userRepository.save(user);

        // Invalidate active refresh tokens so new permissions take effect
        refreshTokenService.revokeAllUserTokens(id, "ROLES_ASSIGNED");

        auditLogService.log("USER_ROLES_ASSIGNED", "User", id.toString(),
                saved.getEmail(), AuditOutcome.SUCCESS, null, "Roles updated to: "
                        + roles.stream().map(Role::getName).collect(Collectors.joining(", ")));

        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    @Transactional
    public UserResponse removeRole(UUID id, UUID roleId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));

        Role roleToRemove = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId.toString()));

        boolean removed = user.getRoles().removeIf(r -> r.getId().equals(roleId));
        if (!removed) {
            throw new InvalidRequestException("Role '" + roleToRemove.getName() + "' is not assigned to this user.");
        }

        User saved = userRepository.save(user);
        refreshTokenService.revokeAllUserTokens(id, "ROLE_REMOVED");

        auditLogService.log("USER_ROLE_REMOVED", "User", id.toString(),
                saved.getEmail(), AuditOutcome.SUCCESS, null, "Role " + roleToRemove.getName() + " removed");

        return toResponse(saved);
    }

    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Transactional
    public void adminResetPassword(UUID id, AdminResetPasswordRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        refreshTokenService.revokeAllUserTokens(id, "ADMIN_PASSWORD_RESET");

        auditLogService.log("USER_PASSWORD_RESET", "User", id.toString(),
                user.getEmail(), AuditOutcome.SUCCESS, null, "Admin reset password for user");
    }

    @PreAuthorize("hasAuthority('USER_DISABLE')")
    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));

        checkNotSelfStatusChange(user, AccountStatus.SUSPENDED);

        // Soft delete: suspend and clear roles
        user.setAccountStatus(AccountStatus.SUSPENDED);
        user.getRoles().clear();
        userRepository.save(user);

        refreshTokenService.revokeAllUserTokens(id, "USER_DELETED");

        auditLogService.log("USER_DELETE", "User", id.toString(),
                user.getEmail(), AuditOutcome.SUCCESS, null, "User account suspended/soft-deleted");
    }

    public UserResponse toResponse(User user) {
        List<RoleResponse> roleResponses = user.getRoles().stream()
                .sorted(Comparator.comparing(Role::getName))
                .map(r -> RoleResponse.builder()
                        .id(r.getId())
                        .name(r.getName())
                        .description(r.getDescription())
                        .systemDefined(r.isSystemDefined())
                        .permissions(r.getPermissions().stream()
                                .sorted(Comparator.comparing(p -> p.getName()))
                                .map(p -> PermissionResponse.builder()
                                        .id(p.getId())
                                        .name(p.getName())
                                        .description(p.getDescription())
                                        .resource(p.getResource())
                                        .action(p.getAction())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .mobileNumber(user.getMobileNumber())
                .accountStatus(user.getAccountStatus() != null ? user.getAccountStatus().name() : "ACTIVE")
                .roles(roleResponses)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .createdByName(user.getCreatedByName())
                .updatedByName(user.getUpdatedByName())
                .build();
    }

    private void checkNotSelfStatusChange(User targetUser, AccountStatus newStatus) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserDetails currentUser) {
            if (currentUser.getUserId().equals(targetUser.getId()) && newStatus != AccountStatus.ACTIVE) {
                throw new InvalidRequestException("Administrators cannot disable or suspend their own account.");
            }
        }
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
            safeOrders.add(Sort.Order.desc("createdAt"));
        }

        int pageSize = Math.min(pageable.getPageSize(), 100);
        return PageRequest.of(pageable.getPageNumber(), pageSize, Sort.by(safeOrders));
    }
}

