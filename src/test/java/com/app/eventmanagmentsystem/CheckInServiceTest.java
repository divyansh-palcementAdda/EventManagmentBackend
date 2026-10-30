package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.exceptions.*;
import com.app.eventmanagmentsystem.model.*;
import com.app.eventmanagmentsystem.model.enums.*;
import com.app.eventmanagmentsystem.payload.checkin.CheckInRequest;
import com.app.eventmanagmentsystem.payload.checkin.CheckInResponse;
import com.app.eventmanagmentsystem.payload.pass.ActivatePassRequest;
import com.app.eventmanagmentsystem.payload.pass.GeneratePassRequest;
import com.app.eventmanagmentsystem.payload.pass.PassResponse;
import com.app.eventmanagmentsystem.repository.*;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.service.impl.CheckInService;
import com.app.eventmanagmentsystem.service.impl.PassActivationService;
import com.app.eventmanagmentsystem.service.impl.PassGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CheckInServiceTest {

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
    private CheckInRepository checkInRepository;

    @Autowired
    private PassGenerationService passGenerationService;

    @Autowired
    private PassActivationService passActivationService;

    @Autowired
    private CheckInService checkInService;

    private Event testEvent;
    private Event otherEvent;
    private User gatekeeperUser;
    private User unauthorizedUser;

    @BeforeEach
    void setUp() {
        Permission scanPerm = permissionRepository.findByName("CHECKIN_SCAN")
                .orElseGet(() -> permissionRepository.save(new Permission("CHECKIN_SCAN", "Scan", "CHECKIN", "SCAN")));
        Permission passGenPerm = permissionRepository.findByName("PASS_GENERATE")
                .orElseGet(() -> permissionRepository.save(new Permission("PASS_GENERATE", "Gen", "PASS", "GENERATE")));

        Role gatekeeperRole = roleRepository.findByName("GATEKEEPER")
                .orElseGet(() -> roleRepository.save(new Role("GATEKEEPER", "Gatekeeper", true)));
        Set<Permission> perms = new HashSet<>(gatekeeperRole.getPermissions());
        perms.add(scanPerm);
        perms.add(passGenPerm);
        gatekeeperRole.setPermissions(perms);
        roleRepository.saveAndFlush(gatekeeperRole);

        gatekeeperUser = userRepository.findByEmail("gatekeeper@eventpass.test").orElseGet(() -> {
            User u = new User();
            u.setEmail("gatekeeper@eventpass.test");
            u.setPasswordHash("hashed_pw");
            u.setFullName("Assigned Gatekeeper");
            u.setRoles(Set.of(gatekeeperRole));
            return userRepository.saveAndFlush(u);
        });

        unauthorizedUser = userRepository.findByEmail("unauth@eventpass.test").orElseGet(() -> {
            User u = new User();
            u.setEmail("unauth@eventpass.test");
            u.setPasswordHash("hashed_pw");
            u.setFullName("Unassigned Staff");
            u.setRoles(Set.of(gatekeeperRole));
            return userRepository.saveAndFlush(u);
        });

        testEvent = new Event();
        testEvent.setName("Main Gala 2026");
        testEvent.setEventCode("MG26");
        testEvent.setPassSeriesCode("A1B2C3D4");
        testEvent.setStatus(EventStatus.ACTIVE);
        testEvent.setActivationPolicy(ActivationPolicy.SELF_SERVICE);
        testEvent.setCheckInPolicy(CheckInPolicy.SINGLE_ENTRY);
        testEvent.setTimezone("UTC");
        testEvent = eventRepository.saveAndFlush(testEvent);

        otherEvent = new Event();
        otherEvent.setName("Side Conference 2026");
        otherEvent.setEventCode("SC26");
        otherEvent.setPassSeriesCode("E5F6G7H8");
        otherEvent.setStatus(EventStatus.ACTIVE);
        otherEvent.setActivationPolicy(ActivationPolicy.SELF_SERVICE);
        otherEvent.setCheckInPolicy(CheckInPolicy.SINGLE_ENTRY);
        otherEvent.setTimezone("UTC");
        otherEvent = eventRepository.saveAndFlush(otherEvent);

        EventGatekeeper assignment = new EventGatekeeper();
        assignment.setEvent(testEvent);
        assignment.setUser(gatekeeperUser);
        assignment.setAssignedAt(Instant.now());
        assignment.setActive(true);
        gatekeeperRepository.saveAndFlush(assignment);
    }

    private void authenticateAs(User user) {
        AppUserDetails ud = new AppUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Check-in: successful check-in records check-in and prevents second scan")
    void successfulCheckInSingleUse() {
        authenticateAs(gatekeeperUser);

        // 1. Generate pass
        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(testEvent.getId());
        genReq.setBatchSize(1);
        PassResponse pass = passGenerationService.generatePasses(genReq).get(0);

        // 2. Activate pass
        ActivatePassRequest actReq = new ActivatePassRequest();
        actReq.setEventId(testEvent.getId());
        actReq.setRawToken(pass.getRawToken());
        actReq.setAttendeeFullName("Alice Smith");
        actReq.setAttendeeEmail("alice@example.com");
        passActivationService.activatePass(actReq);

        // 3. Perform check-in
        CheckInRequest ciReq = new CheckInRequest();
        ciReq.setEventId(testEvent.getId());
        ciReq.setRawToken(pass.getRawToken());
        ciReq.setRequestId("req-scan-001");

        CheckInResponse response = checkInService.performCheckIn(ciReq);
        assertThat(response.getCheckInId()).isNotNull();
        assertThat(response.getAttendeeName()).isEqualTo("Alice Smith");
        assertThat(response.getPassNumber()).isEqualTo(pass.getPassNumber());

        // 4. Second check-in must be rejected
        assertThatThrownBy(() -> checkInService.performCheckIn(ciReq))
                .isInstanceOf(PassAlreadyCheckedInException.class);
    }

    @Test
    @DisplayName("Check-in: unactivated pass is rejected")
    void unactivatedPassRejected() {
        authenticateAs(gatekeeperUser);

        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(testEvent.getId());
        genReq.setBatchSize(1);
        PassResponse pass = passGenerationService.generatePasses(genReq).get(0);

        CheckInRequest ciReq = new CheckInRequest();
        ciReq.setEventId(testEvent.getId());
        ciReq.setRawToken(pass.getRawToken());

        assertThatThrownBy(() -> checkInService.performCheckIn(ciReq))
                .isInstanceOf(PassNotActivatedException.class);
    }

    @Test
    @DisplayName("Check-in: unauthorized gatekeeper (not assigned to event) is rejected")
    void unassignedGatekeeperRejected() {
        authenticateAs(unauthorizedUser);

        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(testEvent.getId());
        genReq.setBatchSize(1);
        PassResponse pass = passGenerationService.generatePasses(genReq).get(0);

        ActivatePassRequest actReq = new ActivatePassRequest();
        actReq.setEventId(testEvent.getId());
        actReq.setRawToken(pass.getRawToken());
        actReq.setAttendeeFullName("Bob");
        actReq.setAttendeeEmail("bob@example.com");
        passActivationService.activatePass(actReq);

        CheckInRequest ciReq = new CheckInRequest();
        ciReq.setEventId(testEvent.getId());
        ciReq.setRawToken(pass.getRawToken());

        assertThatThrownBy(() -> checkInService.performCheckIn(ciReq))
                .isInstanceOf(EventAccessDeniedException.class);
    }

    @Test
    @DisplayName("Check-in: wrong event pass rejected")
    void passFromDifferentEventRejected() {
        authenticateAs(gatekeeperUser);

        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(otherEvent.getId());
        genReq.setBatchSize(1);
        PassResponse pass = passGenerationService.generatePasses(genReq).get(0);

        ActivatePassRequest actReq = new ActivatePassRequest();
        actReq.setEventId(otherEvent.getId());
        actReq.setRawToken(pass.getRawToken());
        actReq.setAttendeeFullName("Charlie");
        actReq.setAttendeeEmail("charlie@example.com");
        passActivationService.activatePass(actReq);

        CheckInRequest ciReq = new CheckInRequest();
        ciReq.setEventId(testEvent.getId());
        ciReq.setRawToken(pass.getRawToken());

        assertThatThrownBy(() -> checkInService.performCheckIn(ciReq))
                .isInstanceOf(InvalidQrTokenException.class);
    }
}
