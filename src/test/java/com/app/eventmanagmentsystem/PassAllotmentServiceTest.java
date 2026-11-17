package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.exceptions.EventAccessDeniedException;
import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.model.*;
import com.app.eventmanagmentsystem.model.enums.*;
import com.app.eventmanagmentsystem.payload.allotment.*;
import com.app.eventmanagmentsystem.payload.checkin.CheckInRequest;
import com.app.eventmanagmentsystem.payload.pass.ActivatePassRequest;
import com.app.eventmanagmentsystem.payload.pass.GeneratePassRequest;
import com.app.eventmanagmentsystem.payload.pass.PassResponse;
import com.app.eventmanagmentsystem.repository.*;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.service.impl.CheckInService;
import com.app.eventmanagmentsystem.service.impl.PassActivationService;
import com.app.eventmanagmentsystem.service.impl.PassAllotmentService;
import com.app.eventmanagmentsystem.service.impl.PassGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PassAllotmentServiceTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventDayRepository eventDayRepository;

    @Autowired
    private EventPassRepository passRepository;

    @Autowired
    private PassAllotmentRepository passAllotmentRepository;

    @Autowired
    private PassCategoryRepository passCategoryRepository;

    @Autowired
    private EventPassCategoryRepository eventPassCategoryRepository;

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
    private PassAllotmentService passAllotmentService;

    @Autowired
    private PassActivationService passActivationService;

    @Autowired
    private CheckInService checkInService;

    private Event event;
    private EventDay day1;
    private User adminUser;
    private User gatekeeperUser;
    private User unauthorizedUser;

    @BeforeEach
    void setUp() {
        Permission allotManagePerm = permissionRepository.findByName("PASS_ALLOT_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("PASS_ALLOT_MANAGE", "Manage allotments", "PASS_ALLOT", "MANAGE")));
        Permission allotViewPerm = permissionRepository.findByName("PASS_ALLOT_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("PASS_ALLOT_VIEW", "View allotments", "PASS_ALLOT", "VIEW")));
        Permission passGenPerm = permissionRepository.findByName("PASS_GENERATE")
                .orElseGet(() -> permissionRepository.save(new Permission("PASS_GENERATE", "Gen", "PASS", "GENERATE")));
        Permission passViewPerm = permissionRepository.findByName("PASS_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("PASS_VIEW", "View", "PASS", "VIEW")));
        Permission checkInScanPerm = permissionRepository.findByName("CHECKIN_SCAN")
                .orElseGet(() -> permissionRepository.save(new Permission("CHECKIN_SCAN", "Scan", "CHECKIN", "SCAN")));

        Role adminRole = roleRepository.findByName("EVENT_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("EVENT_ADMIN", "Event Admin", false)));
        Set<Permission> adminPerms = new HashSet<>(adminRole.getPermissions());
        adminPerms.add(allotManagePerm);
        adminPerms.add(allotViewPerm);
        adminPerms.add(passGenPerm);
        adminPerms.add(passViewPerm);
        adminRole.setPermissions(adminPerms);
        roleRepository.saveAndFlush(adminRole);

        Role gkRole = roleRepository.findByName("GATEKEEPER")
                .orElseGet(() -> roleRepository.save(new Role("GATEKEEPER", "Gatekeeper", true)));
        Set<Permission> gkPerms = new HashSet<>(gkRole.getPermissions());
        gkPerms.add(checkInScanPerm);
        gkPerms.add(passViewPerm);
        gkRole.setPermissions(gkPerms);
        roleRepository.saveAndFlush(gkRole);

        Role viewerRole = roleRepository.findByName("VIEWER")
                .orElseGet(() -> roleRepository.save(new Role("VIEWER", "Viewer", true)));

        adminUser = userRepository.findByEmail("allot_admin@test.local").orElseGet(() -> {
            User u = new User();
            u.setEmail("allot_admin@test.local");
            u.setPasswordHash("hash");
            u.setFullName("Allotment Admin");
            u.setRoles(Set.of(adminRole));
            return userRepository.saveAndFlush(u);
        });

        gatekeeperUser = userRepository.findByEmail("allot_gk@test.local").orElseGet(() -> {
            User u = new User();
            u.setEmail("allot_gk@test.local");
            u.setPasswordHash("hash");
            u.setFullName("Allotment Gatekeeper");
            u.setRoles(Set.of(gkRole));
            return userRepository.saveAndFlush(u);
        });

        unauthorizedUser = userRepository.findByEmail("allot_unauth@test.local").orElseGet(() -> {
            User u = new User();
            u.setEmail("allot_unauth@test.local");
            u.setPasswordHash("hash");
            u.setFullName("Unauthorized Viewer");
            u.setRoles(Set.of(viewerRole));
            return userRepository.saveAndFlush(u);
        });

        event = new Event();
        event.setName("Annual Festival 2026");
        event.setEventCode("AF26");
        event.setPassSeriesCode("AF");
        event.setStatus(EventStatus.ACTIVE);
        event.setActivationPolicy(ActivationPolicy.SELF_SERVICE);
        event.setCheckInPolicy(CheckInPolicy.SINGLE_ENTRY);
        event.setTimezone("UTC");
        event = eventRepository.saveAndFlush(event);

        day1 = new EventDay(event, 1, LocalDate.now(), "Festival Day 1");
        day1 = eventDayRepository.saveAndFlush(day1);

        EventGatekeeper gk = new EventGatekeeper();
        gk.setEvent(event);
        gk.setUser(gatekeeperUser);
        gk.setAssignedAt(Instant.now());
        gk.setActive(true);
        gatekeeperRepository.saveAndFlush(gk);
    }

    private void authenticateAs(User u) {
        AppUserDetails ud = new AppUserDetails(u);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));
    }

    @Test
    @DisplayName("Scenarios 1-4: Allot 50 Guest & 50 Student to Divyansh, verify grouped total 100 & separate category breakdown")
    void testAllotPasses_GroupedTotalAndCategoryBreakdown() {
        authenticateAs(adminUser);

        // 1. Generate 100 GUEST passes and 100 STUDENT passes
        GeneratePassRequest genGuest = new GeneratePassRequest();
        genGuest.setEventId(event.getId());
        genGuest.setBatchSize(100);
        genGuest.setCategoryCode("GUEST");
        passGenerationService.generatePasses(genGuest);

        GeneratePassRequest genStudent = new GeneratePassRequest();
        genStudent.setEventId(event.getId());
        genStudent.setBatchSize(100);
        genStudent.setCategoryCode("STUDENT");
        passGenerationService.generatePasses(genStudent);

        // 2. Discover available sequences for GUEST
        List<PassSequenceOptionResponse> guestSeqs = passAllotmentService.getSequencesForCategory(event.getId(), "GUEST");
        assertThat(guestSeqs).hasSize(1);
        PassSequenceOptionResponse guestSeq = guestSeqs.get(0);
        assertThat(guestSeq.getTotalPasses()).isEqualTo(100);
        assertThat(guestSeq.getAvailablePasses()).isEqualTo(100);

        // 3. Discover available sequences for STUDENT
        List<PassSequenceOptionResponse> studentSeqs = passAllotmentService.getSequencesForCategory(event.getId(), "STUDENT");
        assertThat(studentSeqs).hasSize(1);
        PassSequenceOptionResponse studentSeq = studentSeqs.get(0);
        assertThat(studentSeq.getTotalPasses()).isEqualTo(100);
        assertThat(studentSeq.getAvailablePasses()).isEqualTo(100);

        // Scenario 1: Allot 50 Guest passes to Divyansh
        CreatePassAllotmentRequest req1 = new CreatePassAllotmentRequest();
        req1.setCategoryCode("GUEST");
        req1.setFromSequence(guestSeq.getFromSequence());
        req1.setToSequence(guestSeq.getToSequence());
        req1.setSequenceIdentifier(guestSeq.getDisplayName());
        req1.setAllottedTo("Divyansh");
        req1.setQuantity(50);
        req1.setRemarks("Guest passes batch");
        PassAllotmentResponse res1 = passAllotmentService.createAllotment(event.getId(), req1);
        assertThat(res1.getAllottedQuantity()).isEqualTo(50);

        // Verify remaining available for guest sequence is now 50
        PassSequenceSummaryResponse guestSummary = passAllotmentService.getSequenceSummary(
                event.getId(), "GUEST", guestSeq.getFromSequence(), guestSeq.getToSequence());
        assertThat(guestSummary.getAlreadyAllotted()).isEqualTo(50);
        assertThat(guestSummary.getAvailableToAllot()).isEqualTo(50);

        // Scenario 2: Allot 50 Student passes to Divyansh
        CreatePassAllotmentRequest req2 = new CreatePassAllotmentRequest();
        req2.setCategoryCode("STUDENT");
        req2.setFromSequence(studentSeq.getFromSequence());
        req2.setToSequence(studentSeq.getToSequence());
        req2.setSequenceIdentifier(studentSeq.getDisplayName());
        req2.setAllottedTo("Divyansh");
        req2.setQuantity(50);
        req2.setRemarks("Student passes batch");
        PassAllotmentResponse res2 = passAllotmentService.createAllotment(event.getId(), req2);
        assertThat(res2.getAllottedQuantity()).isEqualTo(50);

        // Scenario 3: Verify the grouped total displays 100 for Divyansh
        PassAllotmentSummaryResponse summary = passAllotmentService.getAllotmentsSummary(event.getId());
        assertThat(summary.getOverallStats().getTotalAllotted()).isEqualTo(100);
        assertThat(summary.getOverallStats().getTotalAvailable()).isEqualTo(100);
        assertThat(summary.getOverallStats().getTotalActivated()).isEqualTo(0);
        assertThat(summary.getOverallStats().getTotalCheckedIn()).isEqualTo(0);

        assertThat(summary.getRecipients()).hasSize(1);
        PassAllotmentRecipientSummary divyanshSummary = summary.getRecipients().get(0);
        assertThat(divyanshSummary.getAllottedTo()).isEqualTo("Divyansh");
        assertThat(divyanshSummary.getTotalAllotted()).isEqualTo(100);
        assertThat(divyanshSummary.getTotalAvailable()).isEqualTo(100);

        // Scenario 4: Verify Guest and Student counts are shown separately
        List<PassAllotmentCategoryDetail> categories = divyanshSummary.getCategories();
        assertThat(categories).hasSize(2);

        PassAllotmentCategoryDetail guestDetail = categories.stream()
                .filter(c -> "GUEST".equalsIgnoreCase(c.getCategoryCode()))
                .findFirst().orElseThrow();
        assertThat(guestDetail.getAllotted()).isEqualTo(50);
        assertThat(guestDetail.getAvailable()).isEqualTo(50);
        assertThat(guestDetail.getActivated()).isEqualTo(0);
        assertThat(guestDetail.getCheckedIn()).isEqualTo(0);

        PassAllotmentCategoryDetail studentDetail = categories.stream()
                .filter(c -> "STUDENT".equalsIgnoreCase(c.getCategoryCode()))
                .findFirst().orElseThrow();
        assertThat(studentDetail.getAllotted()).isEqualTo(50);
        assertThat(studentDetail.getAvailable()).isEqualTo(50);
    }

    @Test
    @DisplayName("Scenarios 5-8: Activation updates activated & available counts; Check-in updates checked-in counts (subset of activated)")
    void testActivationAndCheckInUpdatesCounts() {
        authenticateAs(adminUser);

        // 1. Generate 50 passes and allot 20 to Divyansh
        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(event.getId());
        genReq.setBatchSize(50);
        genReq.setCategoryCode("GUEST");
        List<PassResponse> generated = passGenerationService.generatePasses(genReq);

        CreatePassAllotmentRequest allotReq = new CreatePassAllotmentRequest();
        allotReq.setCategoryCode("GUEST");
        allotReq.setFromSequence(1L);
        allotReq.setToSequence(50L);
        allotReq.setAllottedTo("Divyansh");
        allotReq.setQuantity(20);
        passAllotmentService.createAllotment(event.getId(), allotReq);

        // Verify initial state: Allotted=20, Activated=0, Available=20, CheckedIn=0
        PassAllotmentSummaryResponse initialSummary = passAllotmentService.getAllotmentsSummary(event.getId());
        PassAllotmentRecipientSummary recipient = initialSummary.getRecipients().get(0);
        assertThat(recipient.getTotalAllotted()).isEqualTo(20);
        assertThat(recipient.getTotalActivated()).isEqualTo(0);
        assertThat(recipient.getTotalAvailable()).isEqualTo(20);
        assertThat(recipient.getTotalCheckedIn()).isEqualTo(0);

        // Scenario 5 & 6: Activate 5 passes allotted to Divyansh
        SecurityContextHolder.clearContext(); // Unauthenticated end-user activation
        for (int i = 0; i < 5; i++) {
            PassResponse p = generated.get(i);
            ActivatePassRequest actReq = new ActivatePassRequest();
            actReq.setEventId(event.getId());
            actReq.setRawToken(p.getPassNumber());
            actReq.setAttendeeFullName("Attendee " + i);
            actReq.setAttendeeEmail("attendee" + i + "@event.test");
            passActivationService.activatePass(actReq);
        }

        // Verify activated increases to 5, and available decreases to 15 (20 - 5)
        authenticateAs(adminUser);
        PassAllotmentSummaryResponse postActivationSummary = passAllotmentService.getAllotmentsSummary(event.getId());
        PassAllotmentRecipientSummary postActRecipient = postActivationSummary.getRecipients().get(0);
        assertThat(postActRecipient.getTotalAllotted()).isEqualTo(20);
        assertThat(postActRecipient.getTotalActivated()).isEqualTo(5);
        assertThat(postActRecipient.getTotalAvailable()).isEqualTo(15);
        assertThat(postActRecipient.getTotalCheckedIn()).isEqualTo(0);

        // Scenario 7 & 8: Check in 3 of the 5 activated passes
        authenticateAs(gatekeeperUser);
        for (int i = 0; i < 3; i++) {
            PassResponse p = generated.get(i);
            CheckInRequest ciReq = new CheckInRequest();
            ciReq.setEventId(event.getId());
            ciReq.setRawToken(p.getPassNumber());
            ciReq.setEventDayId(day1.getId());
            checkInService.performCheckIn(ciReq);
        }

        // Verify checked-in count is 3 (which is <= activated count 5)
        authenticateAs(adminUser);
        PassAllotmentSummaryResponse postCheckInSummary = passAllotmentService.getAllotmentsSummary(event.getId());
        PassAllotmentRecipientSummary postCiRecipient = postCheckInSummary.getRecipients().get(0);
        assertThat(postCiRecipient.getTotalAllotted()).isEqualTo(20);
        assertThat(postCiRecipient.getTotalActivated()).isEqualTo(5);
        assertThat(postCiRecipient.getTotalAvailable()).isEqualTo(15);
        assertThat(postCiRecipient.getTotalCheckedIn()).isEqualTo(3);
        assertThat(postCiRecipient.getTotalCheckedIn()).isLessThanOrEqualTo(postCiRecipient.getTotalActivated());

        // Check category analytics summary
        PassAllotmentAnalyticsResponse analytics = passAllotmentService.getCategoryAnalytics(
                event.getId(), "Divyansh", "GUEST", null, null, null, PageRequest.of(0, 10));
        assertThat(analytics.getSummary().getTotalAllotted()).isEqualTo(20);
        assertThat(analytics.getSummary().getActivated()).isEqualTo(5);
        assertThat(analytics.getSummary().getAvailable()).isEqualTo(15);
        assertThat(analytics.getSummary().getCheckedIn()).isEqualTo(3);
        assertThat(analytics.getSummary().getActivatedNotCheckedIn()).isEqualTo(2);
    }

    @Test
    @DisplayName("Scenario 9 & 10: Sequences are segregated by category, changing category refreshes sequence list, wrong category sequence rejected")
    void testCategorySequenceSegregationAndRejection() {
        authenticateAs(adminUser);

        // Generate 50 passes for VIP
        GeneratePassRequest genVip = new GeneratePassRequest();
        genVip.setEventId(event.getId());
        genVip.setBatchSize(50);
        genVip.setCategoryCode("VIP");
        passGenerationService.generatePasses(genVip);

        // Generate 50 passes for STUDENT
        GeneratePassRequest genStudent = new GeneratePassRequest();
        genStudent.setEventId(event.getId());
        genStudent.setBatchSize(50);
        genStudent.setCategoryCode("STUDENT");
        passGenerationService.generatePasses(genStudent);

        // Verify VIP sequence list contains only VIP passes
        List<PassSequenceOptionResponse> vipSeqs = passAllotmentService.getSequencesForCategory(event.getId(), "VIP");
        assertThat(vipSeqs).hasSize(1);
        PassSequenceOptionResponse vipSeq = vipSeqs.get(0);
        assertThat(vipSeq.getFromSequence()).isEqualTo(1L);
        assertThat(vipSeq.getToSequence()).isEqualTo(50L);
        assertThat(vipSeq.getCategoryCode()).isEqualTo("VIP");

        // Verify STUDENT sequence list contains only STUDENT passes
        List<PassSequenceOptionResponse> studentSeqs = passAllotmentService.getSequencesForCategory(event.getId(), "STUDENT");
        assertThat(studentSeqs).hasSize(1);
        PassSequenceOptionResponse studentSeq = studentSeqs.get(0);
        assertThat(studentSeq.getFromSequence()).isEqualTo(51L);
        assertThat(studentSeq.getToSequence()).isEqualTo(100L);
        assertThat(studentSeq.getCategoryCode()).isEqualTo("STUDENT");

        // Selecting a STUDENT sequence range while specifying VIP category must be rejected
        CreatePassAllotmentRequest badReq = new CreatePassAllotmentRequest();
        badReq.setCategoryCode("VIP");
        badReq.setFromSequence(studentSeq.getFromSequence()); // 51
        badReq.setToSequence(studentSeq.getToSequence());     // 100
        badReq.setAllottedTo("Imposter");
        badReq.setQuantity(10);

        assertThatThrownBy(() -> passAllotmentService.createAllotment(event.getId(), badReq))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Only 0 unallotted passes remain in sequence");
    }

    @Test
    @DisplayName("Scenario 11 & 13: Over-allotment is rejected, and same pass cannot belong to multiple allotments")
    void testOverAllotmentAndDuplicateAllotmentPrevention() {
        authenticateAs(adminUser);

        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(event.getId());
        genReq.setBatchSize(30);
        genReq.setCategoryCode("SPONSOR");
        passGenerationService.generatePasses(genReq);

        // 1. Allot 25 passes
        CreatePassAllotmentRequest req1 = new CreatePassAllotmentRequest();
        req1.setCategoryCode("SPONSOR");
        req1.setFromSequence(1L);
        req1.setToSequence(30L);
        req1.setAllottedTo("Lead Sponsor");
        req1.setQuantity(25);
        passAllotmentService.createAllotment(event.getId(), req1);

        // 2. Attempting to allot 10 more when only 5 remain must be rejected
        CreatePassAllotmentRequest req2 = new CreatePassAllotmentRequest();
        req2.setCategoryCode("SPONSOR");
        req2.setFromSequence(1L);
        req2.setToSequence(30L);
        req2.setAllottedTo("Second Sponsor");
        req2.setQuantity(10);

        assertThatThrownBy(() -> passAllotmentService.createAllotment(event.getId(), req2))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Cannot allot 10 passes. Only 5 unallotted passes remain in sequence");

        // 3. Allot the remaining 5 passes
        req2.setQuantity(5);
        PassAllotmentResponse res2 = passAllotmentService.createAllotment(event.getId(), req2);
        assertThat(res2.getAllottedQuantity()).isEqualTo(5);

        // 4. Now 0 passes remain. Any further allotment must fail
        CreatePassAllotmentRequest req3 = new CreatePassAllotmentRequest();
        req3.setCategoryCode("SPONSOR");
        req3.setFromSequence(1L);
        req3.setToSequence(30L);
        req3.setAllottedTo("Third Sponsor");
        req3.setQuantity(1);

        assertThatThrownBy(() -> passAllotmentService.createAllotment(event.getId(), req3))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Only 0 unallotted passes remain");
    }

    @Test
    @DisplayName("Scenario 14: RBAC & Permission Enforcement: Users without PASS_ALLOT_MANAGE are rejected")
    void testRbacPermissionEnforcement() {
        authenticateAs(unauthorizedUser);

        CreatePassAllotmentRequest req = new CreatePassAllotmentRequest();
        req.setCategoryCode("GUEST");
        req.setFromSequence(1L);
        req.setToSequence(50L);
        req.setAllottedTo("Attempted Hack");
        req.setQuantity(5);

        assertThatThrownBy(() -> passAllotmentService.createAllotment(event.getId(), req))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Scenario 15: Analytics pagination, search by pass number, and filtering by activation and check-in status")
    void testAnalyticsFiltersAndPagination() {
        authenticateAs(adminUser);

        // Generate 20 passes and allot all 20 to Rahul Sharma
        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(event.getId());
        genReq.setBatchSize(20);
        genReq.setCategoryCode("FACULTY");
        List<PassResponse> passes = passGenerationService.generatePasses(genReq);

        CreatePassAllotmentRequest allotReq = new CreatePassAllotmentRequest();
        allotReq.setCategoryCode("FACULTY");
        allotReq.setFromSequence(1L);
        allotReq.setToSequence(20L);
        allotReq.setAllottedTo("Rahul Sharma");
        allotReq.setQuantity(20);
        passAllotmentService.createAllotment(event.getId(), allotReq);

        // Activate first 5 passes
        SecurityContextHolder.clearContext();
        for (int i = 0; i < 5; i++) {
            ActivatePassRequest act = new ActivatePassRequest();
            act.setEventId(event.getId());
            act.setRawToken(passes.get(i).getPassNumber());
            act.setAttendeeFullName("Prof " + (i == 0 ? "SpecificSearchTarget" : "Name" + i));
            act.setAttendeeEmail("prof" + i + "@college.edu");
            passActivationService.activatePass(act);
        }

        // Check in 2 of the activated passes
        authenticateAs(gatekeeperUser);
        for (int i = 0; i < 2; i++) {
            CheckInRequest cir = new CheckInRequest();
            cir.setEventId(event.getId());
            cir.setRawToken(passes.get(i).getPassNumber());
            cir.setEventDayId(day1.getId());
            checkInService.performCheckIn(cir);
        }

        authenticateAs(adminUser);

        // A. Filter by search "SpecificSearchTarget"
        PassAllotmentAnalyticsResponse searchResp = passAllotmentService.getCategoryAnalytics(
                event.getId(), "Rahul Sharma", "FACULTY", "SpecificSearchTarget", null, null, PageRequest.of(0, 10));
        assertThat(searchResp.getPasses().getContent()).hasSize(1);
        assertThat(searchResp.getPasses().getContent().get(0).getAttendeeName()).isEqualTo("Prof SpecificSearchTarget");

        // B. Filter by ACTIVATED status
        PassAllotmentAnalyticsResponse actResp = passAllotmentService.getCategoryAnalytics(
                event.getId(), "Rahul Sharma", "FACULTY", null, PassStatus.ACTIVATED, null, PageRequest.of(0, 10));
        assertThat(actResp.getPasses().getTotalElements()).isEqualTo(5);

        // C. Filter by NOT_ACTIVATED status
        PassAllotmentAnalyticsResponse unactResp = passAllotmentService.getCategoryAnalytics(
                event.getId(), "Rahul Sharma", "FACULTY", null, PassStatus.NOT_ACTIVATED, null, PageRequest.of(0, 10));
        assertThat(unactResp.getPasses().getTotalElements()).isEqualTo(15);

        // D. Filter by CHECKED_IN
        PassAllotmentAnalyticsResponse checkedInResp = passAllotmentService.getCategoryAnalytics(
                event.getId(), "Rahul Sharma", "FACULTY", null, null, "CHECKED_IN", PageRequest.of(0, 10));
        assertThat(checkedInResp.getPasses().getTotalElements()).isEqualTo(2);

        // E. Filter by NOT_CHECKED_IN
        PassAllotmentAnalyticsResponse notCheckedResp = passAllotmentService.getCategoryAnalytics(
                event.getId(), "Rahul Sharma", "FACULTY", null, null, "NOT_CHECKED_IN", PageRequest.of(0, 10));
        assertThat(notCheckedResp.getPasses().getTotalElements()).isEqualTo(18);

        // F. Pagination test: page size 5
        PassAllotmentAnalyticsResponse page0 = passAllotmentService.getCategoryAnalytics(
                event.getId(), "Rahul Sharma", "FACULTY", null, null, null, PageRequest.of(0, 5));
        assertThat(page0.getPasses().getContent()).hasSize(5);
        assertThat(page0.getPasses().getTotalPages()).isEqualTo(4);
        assertThat(page0.getPasses().getTotalElements()).isEqualTo(20);
    }
}
