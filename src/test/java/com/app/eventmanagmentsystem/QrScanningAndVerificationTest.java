package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.exceptions.EventAccessDeniedException;
import com.app.eventmanagmentsystem.exceptions.InvalidQrTokenException;
import com.app.eventmanagmentsystem.exceptions.PassAlreadyCheckedInException;
import com.app.eventmanagmentsystem.exceptions.PassNotActivatedException;
import com.app.eventmanagmentsystem.exceptions.PassNotValidForDayException;
import com.app.eventmanagmentsystem.exceptions.PassRevokedException;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventDay;
import com.app.eventmanagmentsystem.model.EventGatekeeper;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.Permission;
import com.app.eventmanagmentsystem.model.Role;
import com.app.eventmanagmentsystem.model.User;
import com.app.eventmanagmentsystem.model.enums.ActivationPolicy;
import com.app.eventmanagmentsystem.model.enums.CheckInPolicy;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.checkin.CheckInRequest;
import com.app.eventmanagmentsystem.payload.checkin.CheckInResponse;
import com.app.eventmanagmentsystem.payload.checkin.CheckInVerificationResponse;
import com.app.eventmanagmentsystem.payload.pass.ActivatePassRequest;
import com.app.eventmanagmentsystem.payload.pass.GeneratePassRequest;
import com.app.eventmanagmentsystem.payload.pass.PassResponse;
import com.app.eventmanagmentsystem.payload.pass.ValidatePassResponse;
import com.app.eventmanagmentsystem.repository.EventDayRepository;
import com.app.eventmanagmentsystem.repository.EventGatekeeperRepository;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.repository.PermissionRepository;
import com.app.eventmanagmentsystem.repository.RoleRepository;
import com.app.eventmanagmentsystem.repository.UserRepository;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.service.impl.CheckInService;
import com.app.eventmanagmentsystem.service.impl.PassActivationService;
import com.app.eventmanagmentsystem.service.impl.PassGenerationService;
import com.app.eventmanagmentsystem.util.PassLookupUtil;
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
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QrScanningAndVerificationTest {

        @Autowired
        private EventRepository eventRepository;

        @Autowired
        private EventDayRepository eventDayRepository;

        @Autowired
        private EventPassRepository passRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private RoleRepository roleRepository;

        @Autowired
        private PermissionRepository permissionRepository;

        @Autowired
        private EventGatekeeperRepository gatekeeperRepository;

        @Autowired
        private PassGenerationService passGenerationService;

        @Autowired
        private PassActivationService passActivationService;

        @Autowired
        private CheckInService checkInService;

        @Autowired
        private PassLookupUtil passLookupUtil;

        private Event event;
        private EventDay day1;
        private EventDay day2;
        private User gatekeeperUser;
        private User unassignedUser;

        @BeforeEach
        void setUp() {
                Permission scanPerm = permissionRepository.findByName("CHECKIN_SCAN")
                                .orElseGet(() -> permissionRepository
                                                .save(new Permission("CHECKIN_SCAN", "Scan", "CHECKIN", "SCAN")));
                Permission passGenPerm = permissionRepository.findByName("PASS_GENERATE")
                                .orElseGet(() -> permissionRepository
                                                .save(new Permission("PASS_GENERATE", "Gen", "PASS", "GENERATE")));

                Role gkRole = roleRepository.findByName("GATEKEEPER")
                                .orElseGet(() -> roleRepository.save(new Role("GATEKEEPER", "Gatekeeper", true)));
                Set<Permission> perms = new HashSet<>(gkRole.getPermissions());
                perms.add(scanPerm);
                perms.add(passGenPerm);
                gkRole.setPermissions(perms);
                roleRepository.saveAndFlush(gkRole);

                gatekeeperUser = userRepository.findByEmail("qr_gk@test.local").orElseGet(() -> {
                        User u = new User();
                        u.setEmail("qr_gk@test.local");
                        u.setPasswordHash("hash");
                        u.setFullName("Verified Gatekeeper");
                        u.setRoles(Set.of(gkRole));
                        return userRepository.saveAndFlush(u);
                });

                unassignedUser = userRepository.findByEmail("qr_unassigned@test.local").orElseGet(() -> {
                        User u = new User();
                        u.setEmail("qr_unassigned@test.local");
                        u.setPasswordHash("hash");
                        u.setFullName("Unassigned Staff");
                        u.setRoles(Set.of(gkRole));
                        return userRepository.saveAndFlush(u);
                });

                event = new Event();
                event.setName("Tech Summit 2026");
                event.setEventCode("TS26");
                event.setPassSeriesCode("QRTEST01");
                event.setStatus(EventStatus.ACTIVE);
                event.setActivationPolicy(ActivationPolicy.SELF_SERVICE);
                event.setCheckInPolicy(CheckInPolicy.SINGLE_ENTRY);
                event.setTimezone("UTC");
                event = eventRepository.saveAndFlush(event);

                day1 = new EventDay(event, 1, LocalDate.now().minusDays(1), "Day 1 — Keynotes");
                day1 = eventDayRepository.saveAndFlush(day1);

                day2 = new EventDay(event, 2, LocalDate.now(), "Day 2 — Workshops");
                day2 = eventDayRepository.saveAndFlush(day2);

                EventGatekeeper gkAssignment = new EventGatekeeper();
                gkAssignment.setEvent(event);
                gkAssignment.setUser(gatekeeperUser);
                gkAssignment.setAssignedAt(Instant.now());
                gkAssignment.setActive(true);
                gatekeeperRepository.saveAndFlush(gkAssignment);
        }

        private void authenticateAs(User u) {
                AppUserDetails ud = new AppUserDetails(u);
                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));
        }

        @Test
        @DisplayName("PassLookupUtil extracts pass number correctly from various QR formats")
        void testPassLookupUtilExtraction() {
                String rawPass = "TS26-QRTEST01-0001";
                assertThat(passLookupUtil.extractIdentifier(rawPass)).isEqualTo(rawPass);

                // URL format /activate/{passNumber}
                String url1 = "https://example.com/activate/TS26-QRTEST01-0001?eventId=" + event.getId();
                assertThat(passLookupUtil.extractIdentifier(url1)).isEqualTo(rawPass);
                assertThat(passLookupUtil.extractEventId(url1)).isEqualTo(event.getId());

                // URL format /activate?passNumber=...
                String url2 = "https://example.com/activate?passNumber=TS26-QRTEST01-0001&eventId=" + event.getId();
                assertThat(passLookupUtil.extractIdentifier(url2)).isEqualTo(rawPass);

                // JSON format
                String json = "{\"passNumber\": \"TS26-QRTEST01-0001\", \"eventId\": \"" + event.getId() + "\"}";
                assertThat(passLookupUtil.extractIdentifier(json)).isEqualTo(rawPass);
        }

        @Test
        @DisplayName("Workflow A: Attendee scans unactivated printable QR pass, validates, and activates")
        void testWorkflowA_AttendeeActivationWithPassNumber() {
                authenticateAs(gatekeeperUser);

                // 1. Admin generates pass badge
                GeneratePassRequest genReq = new GeneratePassRequest();
                genReq.setEventId(event.getId());
                genReq.setBatchSize(1);
                PassResponse generated = passGenerationService.generatePasses(genReq).get(0);
                String passNumber = generated.getPassNumber();

                // 2. Attendee phone scans QR badge: URL is
                // https://app.domain/activate/{passNumber}?eventId={eventId}
                String scannedUrl = "https://myeventpass.app/activate/" + passNumber + "?eventId=" + event.getId();

                // Pass validation call by frontend
                ValidatePassResponse validateRes = passActivationService.validatePass(scannedUrl);
                assertThat(validateRes.isValid()).isTrue();
                assertThat(validateRes.isEligibleForActivation()).isTrue();
                assertThat(validateRes.getPassNumber()).isEqualTo(passNumber);
                assertThat(validateRes.getEventName()).isEqualTo("Tech Summit 2026");

                // 3. Attendee submits activation form
                ActivatePassRequest actReq = new ActivatePassRequest();
                actReq.setEventId(event.getId());
                actReq.setRawToken(passNumber);
                actReq.setAttendeeFullName("Bob Wilson");
                actReq.setAttendeeEmail("bob.wilson@example.com");
                actReq.setAttendeeMobileNumber("+1234567890");
                actReq.setAttendeeCollege("MIT");
                actReq.setAttendeeCourse("Computer Science");

                PassResponse activated = passActivationService.activatePass(actReq);
                assertThat(activated.getStatus()).isEqualTo(PassStatus.ACTIVATED);

                // 4. Repeated validation shows already activated (attendee info hidden for
                // public privacy)
                ValidatePassResponse secondValidate = passActivationService.validatePass(passNumber);
                assertThat(secondValidate.isValid()).isTrue();
                assertThat(secondValidate.isEligibleForActivation()).isFalse();
                assertThat(secondValidate.getMessage()).contains("already been activated");
        }

        @Test
        @DisplayName("Workflow B: Gatekeeper verifies pass and confirms check-in for event day")
        void testWorkflowB_GatekeeperVerificationAndCheckIn() {
                authenticateAs(gatekeeperUser);

                // 1. Generate and activate a pass
                GeneratePassRequest genReq = new GeneratePassRequest();
                genReq.setEventId(event.getId());
                genReq.setBatchSize(1);
                genReq.setValidDayIds(List.of(day2.getId())); // Valid only for Day 2
                PassResponse pass = passGenerationService.generatePasses(genReq).get(0);
                String passNumber = pass.getPassNumber();

                // Gatekeeper scans unactivated pass -> Verify endpoint reports NOT_ACTIVATED
                CheckInRequest verifyReq = new CheckInRequest();
                verifyReq.setEventId(event.getId());
                verifyReq.setEventDayId(day2.getId());
                verifyReq.setRawToken(passNumber);

                CheckInVerificationResponse verifyUnactivated = checkInService.verifyPass(verifyReq);
                assertThat(verifyUnactivated.getStatus()).isEqualTo("NOT_ACTIVATED");
                assertThat(verifyUnactivated.isEligibleForCheckIn()).isFalse();

                // Attempting check-in before activation throws PassNotActivatedException
                assertThatThrownBy(() -> checkInService.performCheckIn(verifyReq))
                                .isInstanceOf(PassNotActivatedException.class);

                // 2. Attendee activates pass
                ActivatePassRequest actReq = new ActivatePassRequest();
                actReq.setEventId(event.getId());
                actReq.setRawToken(passNumber);
                actReq.setAttendeeFullName("Carol Danvers");
                actReq.setAttendeeEmail("carol@marvel.test");
                actReq.setAttendeeMobileNumber("9876543210");
                actReq.setAttendeeCollege("Empire State University");
                actReq.setAttendeeCourse("Physics");
                passActivationService.activatePass(actReq);

                // 3. Gatekeeper scans for Day 1 (which this pass is NOT permitted for)
                CheckInRequest day1VerifyReq = new CheckInRequest();
                day1VerifyReq.setEventId(event.getId());
                day1VerifyReq.setEventDayId(day1.getId());
                day1VerifyReq.setRawToken(passNumber);

                CheckInVerificationResponse verifyDay1 = checkInService.verifyPass(day1VerifyReq);
                assertThat(verifyDay1.getStatus()).isEqualTo("NOT_VALID_TODAY");
                assertThat(verifyDay1.isEligibleForCheckIn()).isFalse();

                // Checking in for Day 1 throws PassNotValidForDayException
                assertThatThrownBy(() -> checkInService.performCheckIn(day1VerifyReq))
                                .isInstanceOf(PassNotValidForDayException.class);

                // 4. Gatekeeper scans for Day 2 (Valid day!)
                CheckInVerificationResponse verifyDay2 = checkInService.verifyPass(verifyReq);
                assertThat(verifyDay2.getStatus()).isEqualTo("READY_FOR_CHECKIN");
                assertThat(verifyDay2.isEligibleForCheckIn()).isTrue();
                assertThat(verifyDay2.getAttendeeName()).isEqualTo("Carol Danvers");

                // 5. Gatekeeper confirms check-in
                CheckInResponse checkInResponse = checkInService.performCheckIn(verifyReq);
                assertThat(checkInResponse.getCheckInId()).isNotNull();
                assertThat(checkInResponse.getAttendeeName()).isEqualTo("Carol Danvers");
                assertThat(checkInResponse.getDayNumber()).isEqualTo(2);

                // 6. Scanning again reports ALREADY_CHECKED_IN
                CheckInVerificationResponse verifyAlreadyCheckedIn = checkInService.verifyPass(verifyReq);
                assertThat(verifyAlreadyCheckedIn.getStatus()).isEqualTo("ALREADY_CHECKED_IN");
                assertThat(verifyAlreadyCheckedIn.isEligibleForCheckIn()).isFalse();
                assertThat(verifyAlreadyCheckedIn.isAlreadyCheckedIn()).isTrue();

                // Duplicate check-in is rejected
                assertThatThrownBy(() -> checkInService.performCheckIn(verifyReq))
                                .isInstanceOf(PassAlreadyCheckedInException.class);
        }

        @Test
        @DisplayName("Workflow D: Unassigned gatekeeper or revoked pass is rejected")
        void testWorkflowD_SecurityAndRevocation() {
                // 1. Generate and activate pass
                authenticateAs(gatekeeperUser);
                GeneratePassRequest genReq = new GeneratePassRequest();
                genReq.setEventId(event.getId());
                genReq.setBatchSize(1);
                PassResponse pass = passGenerationService.generatePasses(genReq).get(0);

                ActivatePassRequest actReq = new ActivatePassRequest();
                actReq.setEventId(event.getId());
                actReq.setRawToken(pass.getPassNumber());
                actReq.setAttendeeFullName("Dave Stone");
                actReq.setAttendeeEmail("dave@stone.test");
                actReq.setAttendeeCollege("Columbia University");
                actReq.setAttendeeCourse("Law");
                passActivationService.activatePass(actReq);

                // 2. Unassigned gatekeeper cannot verify or check in
                authenticateAs(unassignedUser);
                CheckInRequest req = new CheckInRequest();
                req.setEventId(event.getId());
                req.setRawToken(pass.getPassNumber());

                assertThatThrownBy(() -> checkInService.verifyPass(req))
                                .isInstanceOf(EventAccessDeniedException.class);
                assertThatThrownBy(() -> checkInService.performCheckIn(req))
                                .isInstanceOf(EventAccessDeniedException.class);

                // 3. Revoked pass
                authenticateAs(gatekeeperUser);
                EventPass passEntity = passRepository.findByPassNumber(pass.getPassNumber()).orElseThrow();
                passEntity.setStatus(PassStatus.REVOKED);
                passEntity.setRevocationReason("Badge stolen");
                passRepository.saveAndFlush(passEntity);

                CheckInVerificationResponse verifyRevoked = checkInService.verifyPass(req);
                assertThat(verifyRevoked.getStatus()).isEqualTo("REVOKED");
                assertThat(verifyRevoked.isEligibleForCheckIn()).isFalse();

                assertThatThrownBy(() -> checkInService.performCheckIn(req))
                                .isInstanceOf(PassRevokedException.class);
        }
}
