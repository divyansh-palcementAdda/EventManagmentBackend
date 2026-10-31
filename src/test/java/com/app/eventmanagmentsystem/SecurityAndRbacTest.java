package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.model.Permission;
import com.app.eventmanagmentsystem.model.Role;
import com.app.eventmanagmentsystem.model.User;
import com.app.eventmanagmentsystem.model.enums.AccountStatus;
import com.app.eventmanagmentsystem.payload.auth.LoginRequest;
import com.app.eventmanagmentsystem.payload.auth.LoginResponse;
import com.app.eventmanagmentsystem.repository.PermissionRepository;
import com.app.eventmanagmentsystem.repository.RoleRepository;
import com.app.eventmanagmentsystem.repository.UserRepository;
import com.app.eventmanagmentsystem.security.jwt.JwtTokenProvider;
import com.app.eventmanagmentsystem.security.jwt.JwtValidationResult;
import com.app.eventmanagmentsystem.service.impl.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SecurityAndRbacTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User activeUser;
    private User disabledUser;

    @BeforeEach
    void setUp() {
        // Permissions are seeded by DataSeeder; retrieve them idempotently
        Permission permView = permissionRepository.findByName("EVENT_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("EVENT_VIEW", "View events", "EVENT", "VIEW")));
        Permission permCreate = permissionRepository.findByName("EVENT_CREATE")
                .orElseGet(() -> permissionRepository.save(new Permission("EVENT_CREATE", "Create events", "EVENT", "CREATE")));

        Role adminRole = roleRepository.findByName("EVENT_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("EVENT_ADMIN", "Admin", true)));
        adminRole.setPermissions(new HashSet<>(Set.of(permView, permCreate)));
        roleRepository.saveAndFlush(adminRole);

        activeUser = userRepository.findByEmail("admin-test@test.com").orElseGet(() -> {
            User u = new User();
            u.setEmail("admin-test@test.com");
            u.setPasswordHash(passwordEncoder.encode("SecurePass123!"));
            u.setFullName("Admin User");
            u.setAccountStatus(AccountStatus.ACTIVE);
            u.setRoles(new HashSet<>(Set.of(adminRole)));
            return userRepository.saveAndFlush(u);
        });

        disabledUser = userRepository.findByEmail("disabled-test@test.com").orElseGet(() -> {
            User u = new User();
            u.setEmail("disabled-test@test.com");
            u.setPasswordHash(passwordEncoder.encode("SecurePass123!"));
            u.setFullName("Disabled User");
            u.setAccountStatus(AccountStatus.DISABLED);
            u.setRoles(new HashSet<>(Set.of(adminRole)));
            return userRepository.saveAndFlush(u);
        });
    }

    @Test
    @DisplayName("JWT: generate access token and validate claims")
    void generateAndValidateJwt() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateAccessToken(
                userId, "user@test.com", Map.of("permissions", Set.of("EVENT_VIEW"))
        );

        assertThat(token).isNotBlank();
        JwtValidationResult result = jwtTokenProvider.validate(token);
        assertThat(result.isValid()).isTrue();
        assertThat(result.getClaims().getSubject()).isEqualTo(userId.toString());
        assertThat(result.getClaims().get("email", String.class)).isEqualTo("user@test.com");
    }

    @Test
    @DisplayName("JWT: malformed token is rejected safely")
    void malformedTokenRejected() {
        JwtValidationResult result = jwtTokenProvider.validate("malformed.jwt.token");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).isNotBlank();
    }

    @Test
    @DisplayName("Auth: successful login returns JWT with roles and roleId (no permissions)")
    void successfulLogin() {
        LoginRequest req = new LoginRequest();
        req.setEmail("admin-test@test.com");
        req.setPassword("SecurePass123!");

        LoginResponse resp = authService.login(req);
        assertThat(resp.getAccessToken()).isNotBlank();
        assertThat(resp.getEmail()).isEqualTo("admin-test@test.com");
        assertThat(resp.getRoles()).contains("EVENT_ADMIN");
        assertThat(resp.getRoleId()).isNotNull();
        assertThat(resp.getRoleName()).isEqualTo("EVENT_ADMIN");

        // Verify JWT token claims do NOT contain permissions
        var claims = jwtTokenProvider.parseAndValidate(resp.getAccessToken());
        assertThat(claims.get("permissions")).isNull();
        assertThat(claims.get("roles")).isNotNull();
    }

    @Test
    @DisplayName("Auth: invalid password throws BadCredentialsException")
    void invalidPasswordThrows() {
        LoginRequest req = new LoginRequest();
        req.setEmail("admin-test@test.com");
        req.setPassword("WrongPassword!");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Auth: disabled account is rejected")
    void disabledAccountRejected() {
        LoginRequest req = new LoginRequest();
        req.setEmail("disabled-test@test.com");
        req.setPassword("SecurePass123!");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(DisabledException.class);
    }
}
