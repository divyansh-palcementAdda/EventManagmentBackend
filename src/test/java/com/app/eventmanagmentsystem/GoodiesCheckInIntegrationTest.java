package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.exceptions.EventAccessDeniedException;
import com.app.eventmanagmentsystem.exceptions.PassAlreadyCheckedInException;
import com.app.eventmanagmentsystem.exceptions.PassNotActivatedException;
import com.app.eventmanagmentsystem.model.*;
import com.app.eventmanagmentsystem.model.enums.*;
import com.app.eventmanagmentsystem.payload.attendee.AttendeeResponse;
import com.app.eventmanagmentsystem.payload.checkin.CheckInRequest;
import com.app.eventmanagmentsystem.payload.checkin.CheckInResponse;
import com.app.eventmanagmentsystem.payload.checkin.CheckInStatsResponse;
import com.app.eventmanagmentsystem.payload.checkin.CheckInVerificationResponse;
import com.app.eventmanagmentsystem.payload.pass.ActivatePassRequest;
import com.app.eventmanagmentsystem.payload.pass.GeneratePassRequest;
import com.app.eventmanagmentsystem.payload.pass.PassResponse;
import com.app.eventmanagmentsystem.payload.pass.PassStatsResponse;
import com.app.eventmanagmentsystem.repository.*;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.service.impl.AttendeeService;
import com.app.eventmanagmentsystem.service.impl.BulkPassService;
import com.app.eventmanagmentsystem.service.impl.CheckInService;
import com.app.eventmanagmentsystem.service.impl.PassActivationService;
import com.app.eventmanagmentsystem.service.impl.PassGenerationService;
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
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class GoodiesCheckInIntegrationTest {

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
    private EventPassRepository passRepository;

    @Autowired
    private PassGenerationService passGenerationService;

    @Autowired
    private PassActivationService passActivationService;

    @Autowired
    private CheckInService checkInService;

    @Autowired
    private BulkPassService bulkPassService;

    @Autowired
    private AttendeeService attendeeService;

    private Event eventA;
    private Event eventB;
    private User gatekeeperUser;
    private User unauthorizedUser;

    @BeforeEach
    void setUp() {
        Permission scanPerm = permissionRepository.findByName("CHECKIN_SCAN")
                .orElseGet(() -> permissionRepository.save(new Permission("CHECKIN_SCAN", "Scan", "CHECKIN", "SCAN")));
        Permission passGenPerm = permissionRepository.findByName("PASS_GENERATE")
                .orElseGet(() -> permissionRepository.save(new Permission("PASS_GENERATE", "Gen", "PASS", "GENERATE")));
        Permission passViewPerm = permissionRepository.findByName("PASS_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("PASS_VIEW", "View", "PASS", "VIEW")));
        Permission attendeeViewPerm = permissionRepository.findByName("ATTENDEE_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("ATTENDEE_VIEW", "View", "ATTENDEE", "VIEW")));

        Role gatekeeperRole = roleRepository.findByName("GATEKEEPER")
                .orElseGet(() -> roleRepository.save(new Role("GATEKEEPER", "Gatekeeper", true)));
        Set<Permission> perms = new HashSet<>(gatekeeperRole.getPermissions());
        perms.add(scanPerm);
        perms.add(passGenPerm);
        perms.add(passViewPerm);
        perms.add(attendeeViewPerm);
        gatekeeperRole.setPermissions(perms);
        roleRepository.saveAndFlush(gatekeeperRole);

        gatekeeperUser = userRepository.findByEmail("goodies_staff@eventpass.test").orElseGet(() -> {
            User u = new User();
            u.setEmail("goodies_staff@eventpass.test");
            u.setPasswordHash("hashed_pw");
            u.setFullName("Gatekeeper Staff");
            u.setRoles(Set.of(gatekeeperRole));
            return userRepository.saveAndFlush(u);
        });

        unauthorizedUser = userRepository.findByEmail("unauth_staff@eventpass.test").orElseGet(() -> {
            User u = new User();
            u.setEmail("unauth_staff@eventpass.test");
            u.setPasswordHash("hashed_pw");
            u.setFullName("Unauthorized Person");
            u.setRoles(Set.of(gatekeeperRole));
            return userRepository.saveAndFlush(u);
        });

        eventA = new Event();
        eventA.setName("Renaissance Coffee Festival");
        eventA.setEventCode("RCF26");
        eventA.setPassSeriesCode("RCFPASS1");
        eventA.setStatus(EventStatus.ACTIVE);
        eventA.setActivationPolicy(ActivationPolicy.SELF_SERVICE);
        eventA.setCheckInPolicy(CheckInPolicy.SINGLE_ENTRY);
        eventA.setTimezone("UTC");
        eventA = eventRepository.saveAndFlush(eventA);

        eventB = new Event();
        eventB.setName("Tech Expo 2026");
        eventB.setEventCode("TEX26");
        eventB.setPassSeriesCode("TEXPAS01");
        eventB.setStatus(EventStatus.ACTIVE);
        eventB.setActivationPolicy(ActivationPolicy.SELF_SERVICE);
        eventB.setCheckInPolicy(CheckInPolicy.SINGLE_ENTRY);
        eventB.setTimezone("UTC");
        eventB = eventRepository.saveAndFlush(eventB);

        // Assign gatekeeper to both Event A and Event B
        EventGatekeeper assignmentA = new EventGatekeeper();
        assignmentA.setEvent(eventA);
        assignmentA.setUser(gatekeeperUser);
        assignmentA.setAssignedAt(Instant.now());
        assignmentA.setActive(true);
        gatekeeperRepository.saveAndFlush(assignmentA);

        EventGatekeeper assignmentB = new EventGatekeeper();
        assignmentB.setEvent(eventB);
        assignmentB.setUser(gatekeeperUser);
        assignmentB.setAssignedAt(Instant.now());
        assignmentB.setActive(true);
        gatekeeperRepository.saveAndFlush(assignmentB);
    }

    private void authenticateAs(User user) {
        AppUserDetails ud = new AppUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private PassResponse createAndActivatePass(Event event, String attendeeName, String email, String mobile) {
        authenticateAs(gatekeeperUser);
        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(event.getId());
        genReq.setBatchSize(1);
        PassResponse pass = passGenerationService.generatePasses(genReq).get(0);

        ActivatePassRequest actReq = new ActivatePassRequest();
        actReq.setEventId(event.getId());
        actReq.setRawToken(pass.getRawToken());
        actReq.setAttendeeFullName(attendeeName);
        actReq.setAttendeeEmail(email);
        actReq.setAttendeeMobileNumber(mobile);
        actReq.setAttendeeCollege("Renaissance University");
        actReq.setAttendeeCourse("Computer Science");
        passActivationService.activatePass(actReq);

        return pass;
    }

    @Test
    @DisplayName("1. Check-in with goodies toggle ON records goodiesProvided=true, timestamp, and staff name")
    void testCheckInWithGoodiesToggleOn() {
        authenticateAs(gatekeeperUser);
        PassResponse pass = createAndActivatePass(eventA, "Ayaan Khan", "ayaan@rcf.test", "9876500001");

        CheckInRequest request = new CheckInRequest();
        request.setEventId(eventA.getId());
        request.setRawToken(pass.getRawToken());
        request.setGoodiesProvided(true);
        request.setRequestId("req-goodies-on");

        CheckInResponse response = checkInService.performCheckIn(request);

        assertThat(response).isNotNull();
        assertThat(response.isGoodiesProvided()).isTrue();
        assertThat(response.getGoodiesProvidedAt()).isNotNull();
        assertThat(response.getGoodiesProvidedByName()).isEqualTo("Gatekeeper Staff");
        assertThat(response.getGoodiesProvidedById()).isEqualTo(gatekeeperUser.getId().toString());

        // Verify pre-verification endpoint now shows already-checked-in with goodies = true
        CheckInRequest verifyReq = new CheckInRequest();
        verifyReq.setEventId(eventA.getId());
        verifyReq.setRawToken(pass.getRawToken());
        CheckInVerificationResponse verifyRes = checkInService.verifyPass(verifyReq);

        assertThat(verifyRes.isAlreadyCheckedIn()).isTrue();
        assertThat(verifyRes.getGoodiesProvided()).isTrue();
        assertThat(verifyRes.getGoodiesProvidedAt()).isNotNull();
        assertThat(verifyRes.getGoodiesProvidedByName()).isEqualTo("Gatekeeper Staff");
    }

    @Test
    @DisplayName("2. Check-in with goodies toggle OFF records goodiesProvided=false and null timestamp")
    void testCheckInWithGoodiesToggleOff() {
        authenticateAs(gatekeeperUser);
        PassResponse pass = createAndActivatePass(eventA, "Sara Ali", "sara@rcf.test", "9876500002");

        CheckInRequest request = new CheckInRequest();
        request.setEventId(eventA.getId());
        request.setRawToken(pass.getRawToken());
        request.setGoodiesProvided(false);
        request.setRequestId("req-goodies-off");

        CheckInResponse response = checkInService.performCheckIn(request);

        assertThat(response).isNotNull();
        assertThat(response.isGoodiesProvided()).isFalse();
        assertThat(response.getGoodiesProvidedAt()).isNull();
        assertThat(response.getGoodiesProvidedByName()).isNull();

        CheckInRequest verifyReq = new CheckInRequest();
        verifyReq.setEventId(eventA.getId());
        verifyReq.setRawToken(pass.getRawToken());
        CheckInVerificationResponse verifyRes = checkInService.verifyPass(verifyReq);

        assertThat(verifyRes.isAlreadyCheckedIn()).isTrue();
        assertThat(verifyRes.getGoodiesProvided()).isFalse();
        assertThat(verifyRes.getGoodiesProvidedAt()).isNull();
    }

    @Test
    @DisplayName("3. Default toggle value is OFF if omitted or null")
    void testDefaultToggleValueIsOff() {
        authenticateAs(gatekeeperUser);
        PassResponse pass = createAndActivatePass(eventA, "Rohan Verma", "rohan@rcf.test", "9876500003");

        CheckInRequest request = new CheckInRequest();
        request.setEventId(eventA.getId());
        request.setRawToken(pass.getRawToken());
        // Do not set goodiesProvided — must default to false
        request.setRequestId("req-default");

        CheckInResponse response = checkInService.performCheckIn(request);

        assertThat(response.isGoodiesProvided()).isFalse();
        assertThat(response.getGoodiesProvidedAt()).isNull();
    }

    @Test
    @DisplayName("4. Goodies status is scoped to the specific event and pass check-in, not global attendee property")
    void testGoodiesSavedAgainstCorrectEventAndPass() {
        authenticateAs(gatekeeperUser);
        PassResponse passA = createAndActivatePass(eventA, "Divyansh Tomar", "divyansh@rcf.test", "9876500004");

        CheckInRequest reqA = new CheckInRequest();
        reqA.setEventId(eventA.getId());
        reqA.setRawToken(passA.getRawToken());
        reqA.setGoodiesProvided(true);
        CheckInResponse resA = checkInService.performCheckIn(reqA);

        CheckIn dbCheckIn = checkInRepository.findById(resA.getCheckInId()).orElseThrow();
        assertThat(dbCheckIn.getEvent().getId()).isEqualTo(eventA.getId());
        assertThat(dbCheckIn.isGoodiesProvided()).isTrue();
        assertThat(dbCheckIn.getGoodiesProvidedByName()).isEqualTo("Gatekeeper Staff");
    }

    @Test
    @DisplayName("5. Same attendee in another event has independent goodies status")
    void testSameAttendeeInAnotherEventIsIndependent() {
        authenticateAs(gatekeeperUser);
        String sharedEmail = "shared_attendee@test.com";
        String sharedMobile = "9876500005";

        PassResponse passA = createAndActivatePass(eventA, "Ayaan Khan", sharedEmail, sharedMobile);
        PassResponse passB = createAndActivatePass(eventB, "Ayaan Khan", sharedEmail, sharedMobile);

        // In Event A: Check in WITH goodies
        CheckInRequest reqA = new CheckInRequest();
        reqA.setEventId(eventA.getId());
        reqA.setRawToken(passA.getRawToken());
        reqA.setGoodiesProvided(true);
        CheckInResponse resA = checkInService.performCheckIn(reqA);

        // In Event B: Check in WITHOUT goodies
        CheckInRequest reqB = new CheckInRequest();
        reqB.setEventId(eventB.getId());
        reqB.setRawToken(passB.getRawToken());
        reqB.setGoodiesProvided(false);
        CheckInResponse resB = checkInService.performCheckIn(reqB);

        assertThat(resA.isGoodiesProvided()).isTrue();
        assertThat(resA.getEventId()).isEqualTo(eventA.getId());

        assertThat(resB.isGoodiesProvided()).isFalse();
        assertThat(resB.getEventId()).isEqualTo(eventB.getId());
    }

    @Test
    @DisplayName("6. Check-in failure does not persist goodies status")
    void testCheckInFailureDoesNotSaveGoodies() {
        authenticateAs(gatekeeperUser);

        // Unactivated pass
        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(eventA.getId());
        genReq.setBatchSize(1);
        PassResponse unactivatedPass = passGenerationService.generatePasses(genReq).get(0);

        CheckInRequest req = new CheckInRequest();
        req.setEventId(eventA.getId());
        req.setRawToken(unactivatedPass.getRawToken());
        req.setGoodiesProvided(true);

        assertThatThrownBy(() -> checkInService.performCheckIn(req))
                .isInstanceOf(PassNotActivatedException.class);

        assertThat(checkInRepository.countGoodiesProvidedByEventId(eventA.getId())).isEqualTo(0);
    }

    @Test
    @DisplayName("7. Duplicate check-in is rejected and does not duplicate goodies records")
    void testDuplicateCheckInDoesNotDuplicateGoodies() {
        authenticateAs(gatekeeperUser);
        PassResponse pass = createAndActivatePass(eventA, "Duplicate Tester", "dup@rcf.test", "9876500007");

        CheckInRequest req = new CheckInRequest();
        req.setEventId(eventA.getId());
        req.setRawToken(pass.getRawToken());
        req.setGoodiesProvided(true);

        CheckInResponse first = checkInService.performCheckIn(req);
        assertThat(first.isGoodiesProvided()).isTrue();

        long checkInsBefore = checkInRepository.countByEventId(eventA.getId());
        long goodiesBefore = checkInRepository.countGoodiesProvidedByEventId(eventA.getId());

        assertThatThrownBy(() -> checkInService.performCheckIn(req))
                .isInstanceOf(PassAlreadyCheckedInException.class);

        assertThat(checkInRepository.countByEventId(eventA.getId())).isEqualTo(checkInsBefore);
        assertThat(checkInRepository.countGoodiesProvidedByEventId(eventA.getId())).isEqualTo(goodiesBefore);
    }

    @Test
    @DisplayName("8. Unauthorized staff cannot check-in or mark goodies")
    void testUnauthorizedStaffCannotMarkGoodies() {
        authenticateAs(gatekeeperUser);
        PassResponse pass = createAndActivatePass(eventA, "Security Tester", "sec@rcf.test", "9876500008");

        authenticateAs(unauthorizedUser);
        CheckInRequest req = new CheckInRequest();
        req.setEventId(eventA.getId());
        req.setRawToken(pass.getRawToken());
        req.setGoodiesProvided(true);

        assertThatThrownBy(() -> checkInService.performCheckIn(req))
                .isInstanceOf(EventAccessDeniedException.class);
    }

    @Test
    @DisplayName("9. Goodies distribution timestamp is recorded correctly")
    void testGoodiesTimestampRecordedCorrectly() {
        authenticateAs(gatekeeperUser);
        PassResponse pass = createAndActivatePass(eventA, "Timestamp Tester", "time@rcf.test", "9876500009");

        Instant before = Instant.now().minus(1, ChronoUnit.SECONDS);

        CheckInRequest req = new CheckInRequest();
        req.setEventId(eventA.getId());
        req.setRawToken(pass.getRawToken());
        req.setGoodiesProvided(true);
        CheckInResponse response = checkInService.performCheckIn(req);

        Instant after = Instant.now().plus(1, ChronoUnit.SECONDS);

        assertThat(response.getGoodiesProvidedAt()).isAfterOrEqualTo(before);
        assertThat(response.getGoodiesProvidedAt()).isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("10. Event dashboard counts: Total Checked In, Goodies Provided, Goodies Not Provided")
    void testEventDashboardCounts() {
        authenticateAs(gatekeeperUser);

        // Attendee 1: checked in with goodies
        PassResponse p1 = createAndActivatePass(eventA, "User One", "user1@rcf.test", "9876500011");
        CheckInRequest r1 = new CheckInRequest();
        r1.setEventId(eventA.getId());
        r1.setRawToken(p1.getRawToken());
        r1.setGoodiesProvided(true);
        checkInService.performCheckIn(r1);

        // Attendee 2: checked in without goodies
        PassResponse p2 = createAndActivatePass(eventA, "User Two", "user2@rcf.test", "9876500012");
        CheckInRequest r2 = new CheckInRequest();
        r2.setEventId(eventA.getId());
        r2.setRawToken(p2.getRawToken());
        r2.setGoodiesProvided(false);
        checkInService.performCheckIn(r2);

        // Attendee 3: activated but NOT checked in
        createAndActivatePass(eventA, "User Three", "user3@rcf.test", "9876500013");

        // Check via CheckInService stats API
        CheckInStatsResponse stats = checkInService.getCheckInStats(eventA.getId(), null);
        assertThat(stats.getTotalCheckedIn()).isEqualTo(2);
        assertThat(stats.getGoodiesProvided()).isEqualTo(1);
        assertThat(stats.getGoodiesNotProvided()).isEqualTo(1);

        // Check via BulkPassService event stats
        PassStatsResponse passStats = bulkPassService.getPassStats(eventA.getId());
        assertThat(passStats.getCheckedInCount()).isEqualTo(2);
        assertThat(passStats.getGoodiesProvidedCount()).isEqualTo(1);
        assertThat(passStats.getGoodiesNotProvidedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("11. Goodies filter returns correct attendee records")
    void testGoodiesFilterReturnsCorrectRecords() {
        authenticateAs(gatekeeperUser);

        PassResponse p1 = createAndActivatePass(eventA, "With Goodies User", "goodies_user@rcf.test", "9876500021");
        CheckInRequest r1 = new CheckInRequest();
        r1.setEventId(eventA.getId());
        r1.setRawToken(p1.getRawToken());
        r1.setGoodiesProvided(true);
        checkInService.performCheckIn(r1);

        PassResponse p2 = createAndActivatePass(eventA, "Without Goodies User", "nogoodies_user@rcf.test", "9876500022");
        CheckInRequest r2 = new CheckInRequest();
        r2.setEventId(eventA.getId());
        r2.setRawToken(p2.getRawToken());
        r2.setGoodiesProvided(false);
        checkInService.performCheckIn(r2);

        // Filter PROVIDED
        Page<AttendeeResponse> providedPage = attendeeService.getAttendees(
                eventA.getId(), null, null, null, null, "PROVIDED", PageRequest.of(0, 10));
        assertThat(providedPage.getContent()).anyMatch(a -> a.getEmail().equals("goodies_user@rcf.test"));
        assertThat(providedPage.getContent()).noneMatch(a -> a.getEmail().equals("nogoodies_user@rcf.test"));

        // Filter NOT_PROVIDED
        Page<AttendeeResponse> notProvidedPage = attendeeService.getAttendees(
                eventA.getId(), null, null, null, null, "NOT_PROVIDED", PageRequest.of(0, 10));
        assertThat(notProvidedPage.getContent()).anyMatch(a -> a.getEmail().equals("nogoodies_user@rcf.test"));
        assertThat(notProvidedPage.getContent()).noneMatch(a -> a.getEmail().equals("goodies_user@rcf.test"));
    }

    @Test
    @DisplayName("12. Authorized staff can update goodies status for an existing check-in")
    void testAuthorizedUpdateGoodiesStatus() {
        authenticateAs(gatekeeperUser);
        PassResponse pass = createAndActivatePass(eventA, "Correction Tester", "correct@rcf.test", "9876500030");

        // Initially checked in with goodies = false
        CheckInRequest req = new CheckInRequest();
        req.setEventId(eventA.getId());
        req.setRawToken(pass.getRawToken());
        req.setGoodiesProvided(false);
        CheckInResponse initial = checkInService.performCheckIn(req);
        assertThat(initial.isGoodiesProvided()).isFalse();

        // Later updated to true
        CheckInResponse updated = checkInService.updateGoodiesStatus(initial.getCheckInId(), true);
        assertThat(updated.isGoodiesProvided()).isTrue();
        assertThat(updated.getGoodiesProvidedAt()).isNotNull();
        assertThat(updated.getGoodiesProvidedByName()).isEqualTo("Gatekeeper Staff");

        // And can be reverted if needed
        CheckInResponse reverted = checkInService.updateGoodiesStatus(initial.getCheckInId(), false);
        assertThat(reverted.isGoodiesProvided()).isFalse();
        assertThat(reverted.getGoodiesProvidedAt()).isNull();
    }
}
