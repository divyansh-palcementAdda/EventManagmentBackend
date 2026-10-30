package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.model.Permission;
import com.app.eventmanagmentsystem.model.Role;
import com.app.eventmanagmentsystem.model.User;
import com.app.eventmanagmentsystem.model.enums.AccountStatus;
import com.app.eventmanagmentsystem.repository.PermissionRepository;
import com.app.eventmanagmentsystem.repository.RoleRepository;
import com.app.eventmanagmentsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Idempotent seed runner.
 *
 * <p>Runs at application startup and seeds:
 * <ol>
 *   <li>Permission catalog</li>
 *   <li>Roles with permission assignments</li>
 *   <li>Bootstrap admin and gatekeeper users (only when {@code app.bootstrap.enabled=true})</li>
 * </ol>
 *
 * <p>Configuration (via environment variables):
 * <pre>
 *   EVENTPASS_BOOTSTRAP_ENABLED=true
 *   EVENTPASS_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
 *   EVENTPASS_BOOTSTRAP_ADMIN_PASSWORD=StrongPassword!
 *   EVENTPASS_BOOTSTRAP_GATEKEEPER_EMAIL=gatekeeper@example.com
 *   EVENTPASS_BOOTSTRAP_GATEKEEPER_PASSWORD=AnotherStrongPassword!
 * </pre>
 *
 * <p>Passwords are never logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.enabled:false}")
    private boolean bootstrapEnabled;

    @Value("${app.bootstrap.admin.email:}")
    private String adminEmail;

    @Value("${app.bootstrap.admin.password:}")
    private String adminPassword;

    @Value("${app.bootstrap.admin.fullname:System Administrator}")
    private String adminFullName;

    @Value("${app.bootstrap.gatekeeper.email:}")
    private String gatekeeperEmail;

    @Value("${app.bootstrap.gatekeeper.password:}")
    private String gatekeeperPassword;

    @Value("${app.bootstrap.gatekeeper.fullname:Demo Gatekeeper}")
    private String gatekeeperFullName;

    // ===== Permission catalog =====
    private static final List<String[]> PERMISSIONS = List.of(
        new String[]{"USER_VIEW",         "View system users",           "USER",       "VIEW"},
        new String[]{"USER_CREATE",       "Create system users",         "USER",       "CREATE"},
        new String[]{"USER_UPDATE",       "Update system users",         "USER",       "UPDATE"},
        new String[]{"USER_DISABLE",      "Disable system users",        "USER",       "DISABLE"},
        new String[]{"ROLE_VIEW",         "View roles",                  "ROLE",       "VIEW"},
        new String[]{"ROLE_CREATE",       "Create roles",                "ROLE",       "CREATE"},
        new String[]{"ROLE_UPDATE",       "Update roles",                "ROLE",       "UPDATE"},
        new String[]{"ROLE_DELETE",       "Delete roles",                "ROLE",       "DELETE"},
        new String[]{"ROLE_ASSIGN",       "Assign roles to users",       "ROLE",       "ASSIGN"},
        new String[]{"PERMISSION_VIEW",   "View permissions",            "PERMISSION", "VIEW"},
        new String[]{"PERMISSION_MANAGE", "Manage permissions",          "PERMISSION", "MANAGE"},
        new String[]{"EVENT_VIEW",        "View events",                 "EVENT",      "VIEW"},
        new String[]{"EVENT_CREATE",      "Create events",               "EVENT",      "CREATE"},
        new String[]{"EVENT_UPDATE",      "Update events",               "EVENT",      "UPDATE"},
        new String[]{"EVENT_CANCEL",      "Cancel events",               "EVENT",      "CANCEL"},
        new String[]{"GATEKEEPER_ASSIGN", "Assign gatekeepers",          "GATEKEEPER", "ASSIGN"},
        new String[]{"GATEKEEPER_REMOVE", "Remove gatekeepers",          "GATEKEEPER", "REMOVE"},
        new String[]{"PASS_VIEW",         "View passes",                 "PASS",       "VIEW"},
        new String[]{"PASS_GENERATE",     "Generate passes",             "PASS",       "GENERATE"},
        new String[]{"PASS_REVOKE",       "Revoke passes",               "PASS",       "REVOKE"},
        new String[]{"PASS_ACTIVATE",     "Activate passes",             "PASS",       "ACTIVATE"},
        new String[]{"ATTENDEE_VIEW",     "View attendees",              "ATTENDEE",   "VIEW"},
        new String[]{"ATTENDEE_UPDATE",   "Update attendee details",     "ATTENDEE",   "UPDATE"},
        new String[]{"CHECKIN_SCAN",      "Perform check-in scan",       "CHECKIN",    "SCAN"},
        new String[]{"CHECKIN_VIEW",      "View check-in records",       "CHECKIN",    "VIEW"},
        new String[]{"REPORT_VIEW",       "View reports",                "REPORT",     "VIEW"},
        new String[]{"AUDIT_LOG_VIEW",    "View audit logs",             "AUDIT_LOG",  "VIEW"},
        new String[]{"NOTIFICATION_VIEW", "View notifications",          "NOTIFICATION","VIEW"}
    );

    // Role → list of permission names
    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
        "SUPER_ADMIN", allPermissionNames(),
        "EVENT_ADMIN", Set.of(
            "EVENT_VIEW","EVENT_CREATE","EVENT_UPDATE","EVENT_CANCEL",
            "GATEKEEPER_ASSIGN","GATEKEEPER_REMOVE",
            "PASS_VIEW","PASS_GENERATE","PASS_REVOKE",
            "ATTENDEE_VIEW","ATTENDEE_UPDATE",
            "CHECKIN_VIEW","REPORT_VIEW","NOTIFICATION_VIEW"
        ),
        "GATEKEEPER", Set.of(
            "EVENT_VIEW","CHECKIN_SCAN","CHECKIN_VIEW",
            "PASS_VIEW","ATTENDEE_VIEW"
        ),
        "VIEWER", Set.of(
            "EVENT_VIEW","PASS_VIEW","ATTENDEE_VIEW",
            "CHECKIN_VIEW","REPORT_VIEW"
        )
    );

    private static Set<String> allPermissionNames() {
        Set<String> names = new LinkedHashSet<>();
        for (String[] p : PERMISSIONS) names.add(p[0]);
        return names;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedPermissions();
        seedRoles();
        if (bootstrapEnabled) {
            seedBootstrapUsers();
        } else {
            log.info("Bootstrap user seeding is disabled (set EVENTPASS_BOOTSTRAP_ENABLED=true to enable).");
        }
    }

    private void seedPermissions() {
        int created = 0;
        for (String[] pd : PERMISSIONS) {
            if (!permissionRepository.existsByName(pd[0])) {
                permissionRepository.save(new Permission(pd[0], pd[1], pd[2], pd[3]));
                created++;
            }
        }
        if (created > 0) log.info("Seeded {} new permissions.", created);
    }

    private void seedRoles() {
        for (Map.Entry<String, Set<String>> entry : ROLE_PERMISSIONS.entrySet()) {
            String roleName = entry.getKey();
            Role role = roleRepository.findByName(roleName).orElseGet(() -> {
                Role r = new Role(roleName, roleName + " role", true);
                return roleRepository.save(r);
            });

            // Update permissions on every run (idempotent)
            Set<Permission> perms = new HashSet<>();
            for (String permName : entry.getValue()) {
                permissionRepository.findByName(permName).ifPresent(perms::add);
            }
            role.setPermissions(perms);
            roleRepository.save(role);
        }
        log.info("Roles seeded/updated: {}", ROLE_PERMISSIONS.keySet());
    }

    private void seedBootstrapUsers() {
        seedUser(adminEmail, adminPassword, adminFullName, "SUPER_ADMIN");
        seedUser(gatekeeperEmail, gatekeeperPassword, gatekeeperFullName, "GATEKEEPER");
    }

    private void seedUser(String email, String password, String fullName, String roleName) {
        if (email == null || email.isBlank()) {
            log.warn("Bootstrap {} email not configured — skipping user creation.", roleName);
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("Bootstrap {} password not configured — skipping user creation.", roleName);
            return;
        }
        String normalizedEmail = email.toLowerCase().trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            log.info("Bootstrap user already exists: {} — skipping.", normalizedEmail);
            return;
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setRoles(Set.of(role));
        userRepository.save(user);
        log.info("Bootstrap user created: {} with role {}", normalizedEmail, roleName);
        // Password is intentionally NOT logged
    }
}

