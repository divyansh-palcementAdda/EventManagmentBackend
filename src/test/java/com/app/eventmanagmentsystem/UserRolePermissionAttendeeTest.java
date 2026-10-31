package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.exceptions.DuplicateResourceException;
import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.*;
import com.app.eventmanagmentsystem.model.enums.*;
import com.app.eventmanagmentsystem.payload.attendee.*;
import com.app.eventmanagmentsystem.payload.auth.LoginResponse;
import com.app.eventmanagmentsystem.payload.permission.PermissionResponse;
import com.app.eventmanagmentsystem.payload.role.*;
import com.app.eventmanagmentsystem.payload.user.*;
import com.app.eventmanagmentsystem.repository.*;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRolePermissionAttendeeTest {

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private AttendeeService attendeeService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private AttendeeRepository attendeeRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private User adminUser;
    private Role superAdminRole;
    private Role eventAdminRole;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        superAdminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("SUPER_ADMIN", "Super Admin", true)));

        eventAdminRole = roleRepository.findByName("EVENT_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("EVENT_ADMIN", "Event Admin", true)));

        adminUser = userRepository.findByEmail("test-admin@eventpass.test")
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail("test-admin@eventpass.test");
                    u.setFullName("Test Administrator");
                    u.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxyz12345678901234567890");
                    u.setAccountStatus(AccountStatus.ACTIVE);
                    u.setRoles(new HashSet<>(List.of(superAdminRole)));
                    return userRepository.save(u);
                });

        AppUserDetails userDetails = new AppUserDetails(adminUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        testEvent = new Event();
        testEvent.setName("Integration Conference " + System.currentTimeMillis());
        testEvent.setEventCode("EVT" + (System.currentTimeMillis() % 100000));
        testEvent.setStatus(EventStatus.PUBLISHED);
        testEvent.setTimezone("UTC");
        testEvent.setStartsAt(Instant.now());
        testEvent.setEndsAt(Instant.now().plusSeconds(86400));
        testEvent = eventRepository.save(testEvent);
    }

    // ===== 1. User Management Tests =====

    @Test
    @DisplayName("Create user, update details, assign roles, change status, and verify search")
    void testUserLifecycle() {
        String email = "operator" + System.currentTimeMillis() + "@test.com";
        CreateUserRequest createReq = new CreateUserRequest();
        createReq.setEmail(email);
        createReq.setPassword("P@ssw0rd1234!");
        createReq.setFullName("Test Operator");
        createReq.setMobileNumber("+1234567890");
        createReq.setRoleIds(Set.of(eventAdminRole.getId()));

        UserResponse created = userService.createUser(createReq);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getEmail()).isEqualTo(email);
        assertThat(created.getFullName()).isEqualTo("Test Operator");
        assertThat(created.getAccountStatus()).isEqualTo("ACTIVE");
        assertThat(created.getRoles()).hasSize(1);

        // Duplicate email must be rejected
        assertThatThrownBy(() -> userService.createUser(createReq))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        // Update user
        UpdateUserRequest updateReq = new UpdateUserRequest();
        updateReq.setFullName("Updated Operator Name");
        updateReq.setAccountStatus(AccountStatus.SUSPENDED);
        updateReq.setRoleIds(Set.of(superAdminRole.getId(), eventAdminRole.getId()));
        UserResponse updated = userService.updateUser(created.getId(), updateReq);

        assertThat(updated.getFullName()).isEqualTo("Updated Operator Name");
        assertThat(updated.getAccountStatus()).isEqualTo("SUSPENDED");
        assertThat(updated.getRoles()).hasSize(2);

        // Search users
        Page<UserResponse> page = userService.getUsers("Updated", AccountStatus.SUSPENDED, null,
                PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting("email").contains(email);
    }

    // ===== 2. Role & Permission Tests =====

    @Test
    @DisplayName("Create custom role, assign permissions, verify protection of system roles")
    void testRoleAndPermissionManagement() {
        Permission p1 = permissionRepository.findByName("USER_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("USER_VIEW", "View users", "USER", "VIEW")));
        Permission p2 = permissionRepository.findByName("EVENT_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("EVENT_VIEW", "View events", "EVENT", "VIEW")));

        CreateRoleRequest roleReq = new CreateRoleRequest();
        roleReq.setName("CUSTOM_AUDITOR_" + System.currentTimeMillis());
        roleReq.setDescription("Auditor role");
        roleReq.setPermissionIds(Set.of(p1.getId(), p2.getId()));

        RoleResponse customRole = roleService.createRole(roleReq);
        assertThat(customRole.getId()).isNotNull();
        assertThat(customRole.getPermissions()).extracting(PermissionResponse::getName)
                .contains("USER_VIEW", "EVENT_VIEW");
        assertThat(customRole.getPermissionNames()).contains("USER_VIEW", "EVENT_VIEW");
        assertThat(customRole.getPermissionIds()).contains(p1.getId(), p2.getId());
        assertThat(customRole.getPermissionCount()).isEqualTo(2);
        assertThat(customRole.isSystemDefined()).isFalse();

        // Replace permissions
        AssignPermissionsRequest replaceReq = new AssignPermissionsRequest(Set.of(p1.getId()));
        RoleResponse replacedRole = roleService.replacePermissions(customRole.getId(), replaceReq);
        assertThat(replacedRole.getPermissionNames()).containsExactly("USER_VIEW");
        assertThat(replacedRole.getPermissionCount()).isEqualTo(1);

        // Get permissions by role ID
        List<PermissionResponse> rolePerms = roleService.getRolePermissions(customRole.getId());
        assertThat(rolePerms).extracting(PermissionResponse::getName).containsExactly("USER_VIEW");

        // System role deletion must fail
        assertThatThrownBy(() -> roleService.deleteRole(superAdminRole.getId()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("System-defined roles cannot be deleted");

        // Custom role can be deleted when no users assigned
        roleService.deleteRole(customRole.getId());
        assertThat(roleRepository.findById(customRole.getId())).isEmpty();
    }

    // ===== 3. Attendee Lifecycle Tests =====

    @Test
    @DisplayName("Create attendee, test uniqueness within event, stats calculation, and search")
    void testAttendeeLifecycle() {
        String attendeeEmail = "attendee" + System.currentTimeMillis() + "@attendee.com";

        CreateAttendeeRequest attReq = new CreateAttendeeRequest();
        attReq.setEventId(testEvent.getId());
        attReq.setFullName("Alice Attendee");
        attReq.setEmail(attendeeEmail);
        attReq.setOrganization("Tech Corp");
        attReq.setDesignation("Software Engineer");

        AttendeeResponse createdAtt = attendeeService.createAttendee(attReq);
        assertThat(createdAtt.getId()).isNotNull();
        assertThat(createdAtt.getEmail()).isEqualTo(attendeeEmail);
        assertThat(createdAtt.getPassId()).isNull();
        assertThat(createdAtt.isHasPass()).isFalse();

        // Duplicate registration for same event must fail
        assertThatThrownBy(() -> attendeeService.createAttendee(attReq))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already registered for event");

        // Stats calculation (event-scoped and global)
        AttendeeStatsResponse stats = attendeeService.getAttendeeStats(testEvent.getId());
        assertThat(stats.getTotalAttendees()).isGreaterThanOrEqualTo(1);
        assertThat(stats.getConfirmedAttendees()).isGreaterThanOrEqualTo(1);

        AttendeeStatsResponse globalStats = attendeeService.getAttendeeStats(null);
        assertThat(globalStats.getTotalAttendees()).isGreaterThanOrEqualTo(1);
        assertThat(globalStats.getConfirmedAttendees()).isGreaterThanOrEqualTo(1);

        // Search attendees with hasPass filter
        Page<AttendeeResponse> page = attendeeService.getAttendees(testEvent.getId(), "Alice",
                RegistrationStatus.CONFIRMED, null, PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting("email").contains(attendeeEmail);

        Page<AttendeeResponse> withoutPassPage = attendeeService.getAttendees(testEvent.getId(), "Alice",
                RegistrationStatus.CONFIRMED, null, false, PageRequest.of(0, 10));
        assertThat(withoutPassPage.getContent()).extracting("email").contains(attendeeEmail);

        Page<AttendeeResponse> withPassPage = attendeeService.getAttendees(testEvent.getId(), "Alice",
                RegistrationStatus.CONFIRMED, null, true, PageRequest.of(0, 10));
        assertThat(withPassPage.getContent()).extracting("email").doesNotContain(attendeeEmail);
    }

    // ===== 4. Refresh Token Security & Rotation Tests =====

    @Test
    @DisplayName("Refresh token issuance, rotation, and reuse detection token-family revocation")
    void testRefreshTokenRotationAndReuseDetection() {
        // 1. Issue initial refresh token
        String token1 = refreshTokenService.createRefreshToken(adminUser);
        assertThat(token1).isNotBlank();

        // Verify token hash is stored, not plain token
        String hash1 = refreshTokenService.hashToken(token1);
        assertThat(refreshTokenRepository.findByTokenHash(hash1)).isPresent();

        // 2. Rotate token (present token1 -> get token2)
        LoginResponse loginResponse = refreshTokenService.rotateRefreshToken(token1);
        String token2 = loginResponse.getRefreshToken();
        assertThat(token2).isNotBlank();
        assertThat(token2).isNotEqualTo(token1);

        // Old token1 should be marked revoked
        RefreshToken record1 = refreshTokenRepository.findByTokenHash(hash1).orElseThrow();
        assertThat(record1.getRevokedAt()).isNotNull();
        assertThat(record1.getRevocationReason()).isEqualTo("ROTATED");

        // 3. Token Reuse Attack: Attacker tries to use old token1 again
        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(token1))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("previously rotated");

        // Because reuse was detected, the entire token family (including token2) must now be revoked!
        String hash2 = refreshTokenService.hashToken(token2);
        RefreshToken record2 = refreshTokenRepository.findByTokenHash(hash2).orElseThrow();
        assertThat(record2.getRevokedAt()).isNotNull();
        assertThat(record2.getRevocationReason()).isEqualTo("Token reuse detected");
    }
}
