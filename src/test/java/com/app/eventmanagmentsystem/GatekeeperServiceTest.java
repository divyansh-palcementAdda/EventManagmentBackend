package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.exceptions.DuplicateResourceException;
import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.Permission;
import com.app.eventmanagmentsystem.model.Role;
import com.app.eventmanagmentsystem.model.User;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.repository.EventGatekeeperRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.repository.PermissionRepository;
import com.app.eventmanagmentsystem.repository.RoleRepository;
import com.app.eventmanagmentsystem.repository.UserRepository;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.service.impl.GatekeeperService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GatekeeperServiceTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private EventGatekeeperRepository gatekeeperRepository;

    @Autowired
    private GatekeeperService gatekeeperService;

    private Event event;
    private User gatekeeperUser;
    private User ordinaryUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        Role gkRole = roleRepository.findByName("GATEKEEPER")
                .orElseGet(() -> roleRepository.save(new Role("GATEKEEPER", "Gatekeeper", true)));
        Role viewerRole = roleRepository.findByName("VIEWER")
                .orElseGet(() -> roleRepository.save(new Role("VIEWER", "Viewer", true)));
        Role adminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("SUPER_ADMIN", "Admin", true)));

        Permission assignPerm = permissionRepository.findByName("GATEKEEPER_ASSIGN")
                .orElseGet(() -> permissionRepository.save(new Permission("GATEKEEPER_ASSIGN", "Assign", "GATEKEEPER", "ASSIGN")));
        Permission removePerm = permissionRepository.findByName("GATEKEEPER_REMOVE")
                .orElseGet(() -> permissionRepository.save(new Permission("GATEKEEPER_REMOVE", "Remove", "GATEKEEPER", "REMOVE")));
        adminRole.setPermissions(new HashSet<>(Set.of(assignPerm, removePerm)));
        roleRepository.saveAndFlush(adminRole);

        adminUser = new User();
        adminUser.setEmail("superadmin@test.com");
        adminUser.setPasswordHash("hash");
        adminUser.setFullName("Super Admin");
        adminUser.setRoles(new HashSet<>(Set.of(adminRole)));
        adminUser = userRepository.saveAndFlush(adminUser);

        gatekeeperUser = new User();
        gatekeeperUser.setEmail("gk@test.com");
        gatekeeperUser.setPasswordHash("hash");
        gatekeeperUser.setFullName("Gatekeeper Person");
        gatekeeperUser.setRoles(new HashSet<>(Set.of(gkRole)));
        gatekeeperUser = userRepository.saveAndFlush(gatekeeperUser);

        ordinaryUser = new User();
        ordinaryUser.setEmail("ordinary@test.com");
        ordinaryUser.setPasswordHash("hash");
        ordinaryUser.setFullName("Ordinary User");
        ordinaryUser.setRoles(new HashSet<>(Set.of(viewerRole)));
        ordinaryUser = userRepository.saveAndFlush(ordinaryUser);

        event = new Event();
        event.setName("Festival 2026");
        event.setEventCode("FEST26");
        event.setPassSeriesCode("FFFF1111");
        event.setStatus(EventStatus.ACTIVE);
        event = eventRepository.saveAndFlush(event);

        AppUserDetails adminDetails = new AppUserDetails(adminUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities()));
    }

    @Test
    @DisplayName("Gatekeeper: assign gatekeeper successfully")
    void assignGatekeeperSuccess() {
        gatekeeperService.assignGatekeeper(event.getId(), gatekeeperUser.getId());
        assertThat(gatekeeperRepository.existsActiveAssignment(event.getId(), gatekeeperUser.getId())).isTrue();
    }

    @Test
    @DisplayName("Gatekeeper: assign user without GATEKEEPER role is rejected")
    void assignUserWithoutRoleRejected() {
        assertThatThrownBy(() -> gatekeeperService.assignGatekeeper(event.getId(), ordinaryUser.getId()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("does not have the GATEKEEPER role");
    }

    @Test
    @DisplayName("Gatekeeper: duplicate active assignment is rejected")
    void duplicateActiveAssignmentRejected() {
        gatekeeperService.assignGatekeeper(event.getId(), gatekeeperUser.getId());
        assertThatThrownBy(() -> gatekeeperService.assignGatekeeper(event.getId(), gatekeeperUser.getId()))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Gatekeeper: deactivation soft-removes access immediately")
    void removeGatekeeperSoftDeactivation() {
        gatekeeperService.assignGatekeeper(event.getId(), gatekeeperUser.getId());
        assertThat(gatekeeperRepository.existsActiveAssignment(event.getId(), gatekeeperUser.getId())).isTrue();

        gatekeeperService.removeGatekeeper(event.getId(), gatekeeperUser.getId());
        assertThat(gatekeeperRepository.existsActiveAssignment(event.getId(), gatekeeperUser.getId())).isFalse();
    }
}
