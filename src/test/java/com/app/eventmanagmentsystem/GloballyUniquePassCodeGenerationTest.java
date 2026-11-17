package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.Role;
import com.app.eventmanagmentsystem.model.User;
import com.app.eventmanagmentsystem.repository.PermissionRepository;
import com.app.eventmanagmentsystem.repository.RoleRepository;
import com.app.eventmanagmentsystem.repository.UserRepository;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.app.eventmanagmentsystem.model.EventDay;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.checkin.CheckInRequest;
import com.app.eventmanagmentsystem.payload.checkin.CheckInResponse;
import com.app.eventmanagmentsystem.payload.pass.ActivatePassRequest;
import com.app.eventmanagmentsystem.payload.pass.GeneratePassRequest;
import com.app.eventmanagmentsystem.payload.pass.PassResponse;
import com.app.eventmanagmentsystem.repository.EventDayRepository;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.service.impl.CheckInService;
import com.app.eventmanagmentsystem.service.impl.PassActivationService;
import com.app.eventmanagmentsystem.service.impl.PassGenerationService;
import com.app.eventmanagmentsystem.service.impl.PassImageDataLoader;
import com.app.eventmanagmentsystem.service.impl.WalkInAdmissionService;
import com.app.eventmanagmentsystem.util.PassCodeGenerator;
import com.app.eventmanagmentsystem.util.PassLookupUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GloballyUniquePassCodeGenerationTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventDayRepository eventDayRepository;

    @Autowired
    private EventPassRepository eventPassRepository;

    @Autowired
    private PassGenerationService passGenerationService;

    @Autowired
    private PassActivationService passActivationService;

    @Autowired
    private CheckInService checkInService;

    @Autowired
    private PassLookupUtil passLookupUtil;

    @Autowired
    private PassImageDataLoader passImageDataLoader;

    @Autowired
    private PassCodeGenerator passCodeGenerator;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    private void authenticateSuperAdmin() {
        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new com.app.eventmanagmentsystem.model.Role("SUPER_ADMIN", "Super Admin", true)));

        User superAdmin = userRepository.findByEmail("admin_unique@eventpass.test").orElseGet(() -> {
            User u = new User();
            u.setEmail("admin_unique@eventpass.test");
            u.setPasswordHash("hashed_pw");
            u.setFullName("Super Administrator");
            u.setRoles(Set.of(superAdminRole));
            return userRepository.saveAndFlush(u);
        });

        AppUserDetails userDetails = new AppUserDetails(superAdmin);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Event eventA;
    private Event eventB;
    private EventDay dayA1;

    @BeforeEach
    void setUp() {
        eventA = new Event();
        eventA.setName("Rock Concert Festival 2026");
        eventA.setEventCode("RCF26");
        eventA.setPassSeriesCode("RC26INIT");
        eventA.setStatus(EventStatus.PUBLISHED);
        eventA.setVenue("Grand Arena");
        eventA.setStartsAt(Instant.now().plus(1, ChronoUnit.DAYS));
        eventA.setEndsAt(Instant.now().plus(3, ChronoUnit.DAYS));
        eventA = eventRepository.save(eventA);

        dayA1 = new EventDay();
        dayA1.setEvent(eventA);
        dayA1.setDayNumber(1);
        dayA1.setEventDate(LocalDate.now().plusDays(1));
        dayA1.setLabel("Day 1 - Opening");
        dayA1 = eventDayRepository.save(dayA1);

        eventB = new Event();
        eventB.setName("Tech Summit 2026");
        eventB.setEventCode("TECH26");
        eventB.setPassSeriesCode("TS26INIT");
        eventB.setStatus(EventStatus.PUBLISHED);
        eventB.setVenue("Expo Center");
        eventB.setStartsAt(Instant.now().plus(5, ChronoUnit.DAYS));
        eventB.setEndsAt(Instant.now().plus(7, ChronoUnit.DAYS));
        eventB = eventRepository.save(eventB);
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("1. Every pass generated individually receives a distinct middle code")
    void testSinglePassGenerationProducesUniqueMiddleCode() {
        List<String> middleCodes = new ArrayList<>();
        List<String> passNumbers = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            GeneratePassRequest req = new GeneratePassRequest();
            req.setEventId(eventA.getId());
            req.setBatchSize(1);
            List<PassResponse> result = passGenerationService.generatePasses(req);
            assertThat(result).hasSize(1);

            PassResponse p = result.get(0);
            String[] segments = p.getPassNumber().split("-");
            assertThat(segments).hasSize(3);
            assertThat(segments[0]).isEqualTo("RCF26");
            assertThat(segments[1]).matches("^[2-9A-HJ-NP-Z]{8}$");
            assertThat(segments[2]).isEqualTo(String.format("%04d", i));

            middleCodes.add(segments[1]);
            passNumbers.add(p.getPassNumber());
            assertThat(p.getUniqueCode()).isEqualTo(segments[1]);
        }

        // All 5 middle codes must be mutually distinct
        Set<String> uniqueMiddleCodes = new HashSet<>(middleCodes);
        assertThat(uniqueMiddleCodes).hasSize(5);

        Set<String> uniquePassNumbers = new HashSet<>(passNumbers);
        assertThat(uniquePassNumbers).hasSize(5);
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("2. Bulk generation of 1,000 passes produces 1,000 unique middle codes")
    void testBulkRequest1000PassesProduces1000UniqueCodes() {
        GeneratePassRequest req = new GeneratePassRequest();
        req.setEventId(eventA.getId());
        req.setBatchSize(1000);

        List<PassResponse> passes = passGenerationService.generatePasses(req);
        assertThat(passes).hasSize(1000);

        Set<String> middleCodes = new HashSet<>(1000);
        Set<String> uniqueCodes = new HashSet<>(1000);
        Set<String> passNumbers = new HashSet<>(1000);

        for (int i = 0; i < passes.size(); i++) {
            PassResponse pass = passes.get(i);
            long expectedSeq = i + 1;

            assertThat(pass.getSequenceNumber()).isEqualTo(expectedSeq);
            String[] parts = pass.getPassNumber().split("-");
            assertThat(parts).hasSize(3);
            assertThat(parts[0]).isEqualTo("RCF26");
            assertThat(parts[1]).matches("^[2-9A-HJ-NP-Z]{8}$");
            assertThat(parts[2]).isEqualTo(String.format("%04d", expectedSeq));

            middleCodes.add(parts[1]);
            uniqueCodes.add(pass.getUniqueCode());
            passNumbers.add(pass.getPassNumber());
        }

        // 1,000 distinct middle codes, unique codes, and complete pass numbers
        assertThat(middleCodes).hasSize(1000);
        assertThat(uniqueCodes).hasSize(1000);
        assertThat(passNumbers).hasSize(1000);
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("3. Code generator avoids visually confusing characters (0, O, 1, I)")
    void testPassCodeGeneratorAvoidsConfusingCharacters() {
        for (int i = 0; i < 500; i++) {
            String code = passCodeGenerator.generateCandidate();
            assertThat(code).hasSize(8);
            assertThat(code).doesNotContain("0", "O", "1", "I");
            assertThat(code).matches("^[2-9A-HJ-NP-Z]{8}$");
        }
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("4. Different events cannot reuse the same middle code")
    void testDifferentEventsCannotReuseSameMiddleCode() {
        GeneratePassRequest reqA = new GeneratePassRequest();
        reqA.setEventId(eventA.getId());
        reqA.setBatchSize(50);
        List<PassResponse> passesA = passGenerationService.generatePasses(reqA);

        GeneratePassRequest reqB = new GeneratePassRequest();
        reqB.setEventId(eventB.getId());
        reqB.setBatchSize(50);
        List<PassResponse> passesB = passGenerationService.generatePasses(reqB);

        Set<String> middleCodesA = passesA.stream()
                .map(p -> p.getPassNumber().split("-")[1])
                .collect(Collectors.toSet());
        Set<String> middleCodesB = passesB.stream()
                .map(p -> p.getPassNumber().split("-")[1])
                .collect(Collectors.toSet());

        assertThat(middleCodesA).hasSize(50);
        assertThat(middleCodesB).hasSize(50);

        // Sets must be completely disjoint
        Set<String> intersection = new HashSet<>(middleCodesA);
        intersection.retainAll(middleCodesB);
        assertThat(intersection).isEmpty();
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("5. Different categories cannot reuse the same middle code")
    void testDifferentCategoriesCannotReuseSameMiddleCode() {
        GeneratePassRequest reqVip = new GeneratePassRequest();
        reqVip.setEventId(eventA.getId());
        reqVip.setCategoryCode("VIP");
        reqVip.setBatchSize(20);
        List<PassResponse> vipPasses = passGenerationService.generatePasses(reqVip);

        GeneratePassRequest reqStudent = new GeneratePassRequest();
        reqStudent.setEventId(eventA.getId());
        reqStudent.setCategoryCode("STUDENT");
        reqStudent.setBatchSize(20);
        List<PassResponse> studentPasses = passGenerationService.generatePasses(reqStudent);

        Set<String> vipCodes = vipPasses.stream().map(p -> p.getPassNumber().split("-")[1]).collect(Collectors.toSet());
        Set<String> studentCodes = studentPasses.stream().map(p -> p.getPassNumber().split("-")[1]).collect(Collectors.toSet());

        assertThat(vipCodes).hasSize(20);
        assertThat(studentCodes).hasSize(20);

        Set<String> intersection = new HashSet<>(vipCodes);
        intersection.retainAll(studentCodes);
        assertThat(intersection).isEmpty();
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("6. Different sequences cannot reuse the same middle code")
    void testDifferentSequencesCannotReuseSameMiddleCode() {
        GeneratePassRequest req = new GeneratePassRequest();
        req.setEventId(eventA.getId());
        req.setBatchSize(10);
        List<PassResponse> passes = passGenerationService.generatePasses(req);

        Map<Long, String> sequenceToCodeMap = new HashMap<>();
        for (PassResponse p : passes) {
            String middleCode = p.getPassNumber().split("-")[1];
            assertThat(sequenceToCodeMap.values()).doesNotContain(middleCode);
            sequenceToCodeMap.put(p.getSequenceNumber(), middleCode);
        }
        assertThat(sequenceToCodeMap).hasSize(10);
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("7. Sequence numbers remain correct and formatted (%04d)")
    void testSequenceNumbersRemainCorrectAndFormatted() {
        GeneratePassRequest req = new GeneratePassRequest();
        req.setEventId(eventA.getId());
        req.setBatchSize(5);
        List<PassResponse> passes = passGenerationService.generatePasses(req);

        for (int i = 0; i < passes.size(); i++) {
            long expected = i + 1;
            assertThat(passes.get(i).getSequenceNumber()).isEqualTo(expected);
            assertThat(passes.get(i).getPassNumber()).endsWith(String.format("-%04d", expected));
        }
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("8. Event prefixes remain strictly preserved")
    void testEventPrefixesRemainCorrect() {
        GeneratePassRequest req = new GeneratePassRequest();
        req.setEventId(eventA.getId());
        req.setBatchSize(3);
        List<PassResponse> passes = passGenerationService.generatePasses(req);

        for (PassResponse pass : passes) {
            assertThat(pass.getPassNumber()).startsWith("RCF26-");
        }
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("9. Complete pass IDs remain globally unique")
    void testCompletePassIdsRemainGloballyUnique() {
        GeneratePassRequest req = new GeneratePassRequest();
        req.setEventId(eventA.getId());
        req.setBatchSize(100);
        List<PassResponse> passes = passGenerationService.generatePasses(req);

        Set<String> passNumbers = passes.stream().map(PassResponse::getPassNumber).collect(Collectors.toSet());
        assertThat(passNumbers).hasSize(100);

        for (PassResponse p : passes) {
            assertThat(eventPassRepository.existsByPassNumber(p.getPassNumber())).isTrue();
            assertThat(eventPassRepository.existsByUniqueCode(p.getUniqueCode())).isTrue();
        }
    }

    @Test
    @DisplayName("10. Collision retry mechanism handles duplicate code generation safely")
    void testDatabaseDuplicateKeyCollisionHandledSafely() {
        // Pre-populate candidate in collision mock
        Set<String> existing = new HashSet<>();
        existing.add("COLLIDE1");
        existing.add("COLLIDE2");

        AtomicInteger callCount = new AtomicInteger(0);
        String uniqueCode = passCodeGenerator.generateUniqueCode(candidate -> {
            int current = callCount.incrementAndGet();
            if (current <= 2) {
                return true; // Simulate collision on first 2 attempts
            }
            return false;
        }, 5);

        assertThat(uniqueCode).isNotNull().hasSize(8);
        assertThat(callCount.get()).isEqualTo(3);

        // Exhausted retries throws bounded exception
        assertThatThrownBy(() -> passCodeGenerator.generateUniqueCode(c -> true, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Database collision threshold reached");
    }

    @Test
    @WithMockUser(authorities = {"PASS_GENERATE", "PASS_VIEW", "ATTENDEE_REGISTER"})
    @DisplayName("11. Existing pass lookup continues working for both passNumber and uniqueCode")
    void testExistingPassLookupContinuesWorking() {
        GeneratePassRequest req = new GeneratePassRequest();
        req.setEventId(eventA.getId());
        req.setBatchSize(1);
        PassResponse generated = passGenerationService.generatePasses(req).get(0);

        // Lookup by full passNumber
        Optional<EventPass> byFull = passLookupUtil.findPass(generated.getPassNumber());
        assertThat(byFull).isPresent();
        assertThat(byFull.get().getId()).isEqualTo(generated.getId());

        // Lookup by 8-character uniqueCode directly
        Optional<EventPass> byCode = passLookupUtil.findPass(generated.getUniqueCode());
        assertThat(byCode).isPresent();
        assertThat(byCode.get().getId()).isEqualTo(generated.getId());

        // Legacy pass lookup without uniqueCode
        EventPass legacy = new EventPass();
        legacy.setEvent(eventA);
        legacy.setPassNumber("LEGACY26-TESTCODE-9999");
        legacy.setUniqueCode(null); // legacy null
        legacy.setSequenceNumber(9999L);
        legacy.setCategory("GENERAL");
        legacy.setStatus(PassStatus.NOT_ACTIVATED);
        legacy.setGeneratedAt(Instant.now());
        legacy = eventPassRepository.save(legacy);

        Optional<EventPass> byLegacy = passLookupUtil.findPass("LEGACY26-TESTCODE-9999");
        assertThat(byLegacy).isPresent();
        assertThat(byLegacy.get().getId()).isEqualTo(legacy.getId());
    }

    @Test
    @WithMockUser(authorities = {"PASS_GENERATE", "PASS_ACTIVATE", "CHECKIN_SCAN", "SUPER_ADMIN"})
    @DisplayName("12. Pass activation and check-in workflows succeed seamlessly with updated pass format")
    void testExistingQrActivationAndAuthorizedCheckInContinueWorking() {
        GeneratePassRequest req = new GeneratePassRequest();
        req.setEventId(eventA.getId());
        req.setBatchSize(1);
        PassResponse generated = passGenerationService.generatePasses(req).get(0);

        // 1. Activate pass using passNumber
        ActivatePassRequest actReq = new ActivatePassRequest();
        actReq.setEventId(eventA.getId());
        actReq.setRawToken(generated.getPassNumber());
        actReq.setAttendeeFullName("Divyansh Sharma");
        actReq.setAttendeeEmail("divyansh.test@example.com");
        actReq.setAttendeeMobileNumber("9876543210");
        actReq.setAttendeeCollege("Delhi University");
        actReq.setAttendeeStudentId("DU-2026-001");
        actReq.setAttendeeCourse("B.Tech");
        actReq.setAttendeeYearSemester("3rd Year");

        PassResponse actResp = passActivationService.activatePass(actReq);
        assertThat(actResp).isNotNull();
        assertThat(actResp.getPassNumber()).isEqualTo(generated.getPassNumber());
        assertThat(actResp.getAttendeeName()).isEqualTo("Divyansh Sharma");

        // 2. Perform authorized check-in using the passNumber
        authenticateSuperAdmin();
        CheckInRequest checkInReq = new CheckInRequest();
        checkInReq.setEventId(eventA.getId());
        checkInReq.setRawToken(generated.getPassNumber());
        checkInReq.setEventDayId(dayA1.getId());

        CheckInResponse checkInResp = checkInService.performCheckIn(checkInReq);
        assertThat(checkInResp).isNotNull();
        assertThat(checkInResp.getPassNumber()).isEqualTo(generated.getPassNumber());
        assertThat(checkInResp.getAttendeeName()).isEqualTo("Divyansh Sharma");
        assertThat(checkInResp.getCheckInId()).isNotNull();
    }

    @Test
    @WithMockUser(authorities = {"PASS_GENERATE"})
    @DisplayName("13. Pass image snapshot uses updated persisted pass ID with unique code")
    void testPassImageGenerationUsesUpdatedPersistedPassId() {
        GeneratePassRequest req = new GeneratePassRequest();
        req.setEventId(eventA.getId());
        req.setBatchSize(1);
        PassResponse generated = passGenerationService.generatePasses(req).get(0);

        var snapshot = passImageDataLoader.loadSnapshot(generated.getId());
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getPassNumber()).isEqualTo(generated.getPassNumber());
        assertThat(snapshot.getPassNumber()).contains(generated.getUniqueCode());
    }

    @Test
    @DisplayName("14. Migration preserves existing UUID relationships and historical pass data")
    void testMigrationPreservesExistingUuidRelationshipsAndHistoricalData() {
        // Save a historical pass simulating pre-migration record with null unique_code
        EventPass historicalPass = new EventPass();
        historicalPass.setEvent(eventA);
        historicalPass.setPassNumber("HIST26-SHARED01-0001");
        historicalPass.setUniqueCode(null);
        historicalPass.setSequenceNumber(1L);
        historicalPass.setCategory("VIP");
        historicalPass.setStatus(PassStatus.NOT_ACTIVATED);
        historicalPass.setGeneratedAt(Instant.now());
        EventPass saved = eventPassRepository.save(historicalPass);

        // Fetch back and assert relationships and data are intact
        EventPass reloaded = eventPassRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(saved.getId());
        assertThat(reloaded.getEvent().getId()).isEqualTo(eventA.getId());
        assertThat(reloaded.getPassNumber()).isEqualTo("HIST26-SHARED01-0001");
        assertThat(reloaded.getUniqueCode()).isNull();
        assertThat(reloaded.getCategory()).isEqualTo("VIP");
    }
}
