package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.model.Permission;
import com.app.eventmanagmentsystem.model.Role;
import com.app.eventmanagmentsystem.model.User;
import com.app.eventmanagmentsystem.model.enums.AccountStatus;
import com.app.eventmanagmentsystem.repository.PermissionRepository;
import com.app.eventmanagmentsystem.repository.RoleRepository;
import com.app.eventmanagmentsystem.repository.UserRepository;
import com.app.eventmanagmentsystem.security.jwt.JwtTokenProvider;
import com.app.eventmanagmentsystem.service.impl.PassImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Regression and authorization tests verifying that URL pattern matching (PathPatternParser)
 * in SecurityConfig does not throw PatternParseException and enforces strict RBAC/public rules.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityPatternAndAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PassImageService passImageService;

    private User adminUser;
    private User viewerUser;
    private String adminToken;
    private String viewerToken;

    @BeforeEach
    void setUp() {
        Permission permView = permissionRepository.findByName("EVENT_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("EVENT_VIEW", "View events", "EVENT", "VIEW")));
        Permission permCreate = permissionRepository.findByName("EVENT_CREATE")
                .orElseGet(() -> permissionRepository.save(new Permission("EVENT_CREATE", "Create events", "EVENT", "CREATE")));
        Permission permWaView = permissionRepository.findByName("WHATSAPP_CONFIG_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("WHATSAPP_CONFIG_VIEW", "View WhatsApp settings", "WHATSAPP", "VIEW")));
        Permission permWaManage = permissionRepository.findByName("WHATSAPP_CONFIG_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("WHATSAPP_CONFIG_MANAGE", "Manage WhatsApp settings", "WHATSAPP", "MANAGE")));

        Role adminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("SUPER_ADMIN", "Super Admin", true)));
        adminRole.setPermissions(new HashSet<>(Set.of(permView, permCreate, permWaView, permWaManage)));
        roleRepository.saveAndFlush(adminRole);

        Role viewerRole = roleRepository.findByName("VIEWER")
                .orElseGet(() -> roleRepository.save(new Role("VIEWER", "Viewer", true)));
        viewerRole.setPermissions(new HashSet<>(Set.of(permView)));
        roleRepository.saveAndFlush(viewerRole);

        adminUser = userRepository.findByEmail("sec-admin@test.com").orElseGet(() -> {
            User u = new User();
            u.setEmail("sec-admin@test.com");
            u.setPasswordHash(passwordEncoder.encode("SecurePass123!"));
            u.setFullName("Security Admin");
            u.setAccountStatus(AccountStatus.ACTIVE);
            u.setRoles(new HashSet<>(Set.of(adminRole)));
            return userRepository.saveAndFlush(u);
        });

        viewerUser = userRepository.findByEmail("sec-viewer@test.com").orElseGet(() -> {
            User u = new User();
            u.setEmail("sec-viewer@test.com");
            u.setPasswordHash(passwordEncoder.encode("SecurePass123!"));
            u.setFullName("Security Viewer");
            u.setAccountStatus(AccountStatus.ACTIVE);
            u.setRoles(new HashSet<>(Set.of(viewerRole)));
            return userRepository.saveAndFlush(u);
        });

        adminToken = jwtTokenProvider.generateAccessToken(
                adminUser.getId(), adminUser.getEmail(),
                Map.of("roles", Set.of("SUPER_ADMIN"), "permissions", Set.of("EVENT_VIEW", "EVENT_CREATE", "WHATSAPP_CONFIG_VIEW", "WHATSAPP_CONFIG_MANAGE"))
        );

        viewerToken = jwtTokenProvider.generateAccessToken(
                viewerUser.getId(), viewerUser.getEmail(),
                Map.of("roles", Set.of("VIEWER"), "permissions", Set.of("EVENT_VIEW"))
        );
    }

    @Test
    @DisplayName("Public endpoint: /api/v1/health is accessible anonymously without PatternParseException")
    void testPublicHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Public endpoint: /actuator/health is accessible anonymously (returns non-401)")
    void testPublicActuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> {
                    assertThat(result.getResponse().getStatus()).isIn(200, 503);
                });
    }

    @Test
    @DisplayName("Public endpoint: /api/v1/passes/{passId}/pass-image serves image without authentication")
    void testPassImageRetrievalPublic() throws Exception {
        UUID passId = UUID.randomUUID();
        byte[] testPngBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        passImageService.savePassImage(passId, testPngBytes);

        mockMvc.perform(get("/api/v1/passes/" + passId + "/pass-image"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                .andExpect(header().string("Cache-Control", "public, max-age=86400"))
                .andExpect(content().bytes(testPngBytes));
    }

    @Test
    @DisplayName("Protected endpoint: nested pass-image path is NOT matched by single-segment wildcard and requires auth")
    void testNestedPassImagePathRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/passes/nested/subpath/pass-image"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Protected endpoint: /uploads/passes/** requires authentication")
    void testUploadsPathRequiresAuth() throws Exception {
        mockMvc.perform(get("/uploads/passes/test.png"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Protected endpoint: unauthenticated access to /api/v1/events returns 401 UNAUTHENTICATED")
    void testProtectedEventsEndpointUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    @DisplayName("Protected endpoint: unauthenticated access to /api/v1/passes/{passId} returns 401")
    void testProtectedPassDetailUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/passes/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Protected endpoint: unauthenticated access to /api/v1/whatsapp/settings returns 401")
    void testProtectedWhatsAppSettingsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/whatsapp/settings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Invalid JWT: malformed or forged token returns 401 UNAUTHENTICATED")
    void testInvalidJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .header("Authorization", "Bearer invalid.token.signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Error dispatch: /error does not throw PatternParseException and is permitted by security")
    void testErrorDispatchPermitted() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr("jakarta.servlet.error.status_code", 404))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("RBAC: admin user with valid token and WHATSAPP_CONFIG_VIEW can access protected settings")
    void testAdminAccessAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/whatsapp/settings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("RBAC: viewer user without WHATSAPP_CONFIG_VIEW permission is forbidden (403 ACCESS_DENIED)")
    void testViewerForbiddenOnAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/whatsapp/settings")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value(containsString("permission")));
    }
}
