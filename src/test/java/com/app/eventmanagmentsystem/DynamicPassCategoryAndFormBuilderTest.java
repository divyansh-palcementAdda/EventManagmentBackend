package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventDay;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.Permission;
import com.app.eventmanagmentsystem.model.Role;
import com.app.eventmanagmentsystem.model.User;
import com.app.eventmanagmentsystem.model.enums.*;
import com.app.eventmanagmentsystem.payload.category.*;
import com.app.eventmanagmentsystem.payload.checkin.CheckInRequest;
import com.app.eventmanagmentsystem.payload.checkin.CheckInResponse;
import com.app.eventmanagmentsystem.payload.pass.AutoActivatePassRequest;
import com.app.eventmanagmentsystem.payload.pass.GeneratePassRequest;
import com.app.eventmanagmentsystem.payload.pass.PassResponse;
import com.app.eventmanagmentsystem.payload.walkin.WalkInAdmitRequest;
import com.app.eventmanagmentsystem.payload.walkin.WalkInAdmitResponse;
import com.app.eventmanagmentsystem.repository.*;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.service.impl.*;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DynamicPassCategoryAndFormBuilderTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventDayRepository eventDayRepository;

    @Autowired
    private PassCategoryRepository passCategoryRepository;

    @Autowired
    private EventPassCategoryRepository eventPassCategoryRepository;

    @Autowired
    private EventPassRepository eventPassRepository;

    @Autowired
    private CheckInRepository checkInRepository;

    @Autowired
    private AttendeeRepository attendeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PassCategoryService passCategoryService;

    @Autowired
    private EventPassCategoryService eventPassCategoryService;

    @Autowired
    private FormBuilderService formBuilderService;

    @Autowired
    private PassGenerationService passGenerationService;

    @Autowired
    private PassActivationService passActivationService;

    @Autowired
    private CheckInService checkInService;

    @Autowired
    private WalkInAdmissionService walkInAdmissionService;

    private Event testEvent;
    private EventDay testDay;
    private User superAdminUser;

    @BeforeEach
    void setUp() {
        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("SUPER_ADMIN", "Super Admin", true)));

        superAdminUser = userRepository.findByEmail("dynamic_admin@test.com").orElseGet(() -> {
            User u = new User();
            u.setEmail("dynamic_admin@test.com");
            u.setPasswordHash("hashed_pw");
            u.setFullName("Super Administrator");
            u.setRoles(Set.of(superAdminRole));
            return userRepository.saveAndFlush(u);
        });

        authenticateAs(superAdminUser);

        testEvent = new Event();
        testEvent.setName("Tech Summit 2026");
        testEvent.setEventCode("TS26");
        testEvent.setPassSeriesCode("ABCD1234");
        testEvent.setStatus(EventStatus.ACTIVE);
        testEvent.setActivationPolicy(ActivationPolicy.SELF_SERVICE);
        testEvent.setCheckInPolicy(CheckInPolicy.SINGLE_ENTRY);
        testEvent.setStartsAt(Instant.now().minusSeconds(3600));
        testEvent.setEndsAt(Instant.now().plusSeconds(86400 * 3));
        testEvent.setMaximumPasses(1000);
        testEvent = eventRepository.save(testEvent);

        testDay = new EventDay(testEvent, 1, LocalDate.now(), "Day 1");
        testDay = eventDayRepository.save(testDay);
    }

    private void authenticateAs(User user) {
        AppUserDetails ud = new AppUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Create Global and Event-Specific categories with isolation")
    void testCategoryCreationAndIsolation() {
        CreatePassCategoryRequest globalReq = new CreatePassCategoryRequest();
        globalReq.setName("VIP Pass");
        globalReq.setCode("VIP_" + UUID.randomUUID().toString().substring(0, 4));
        globalReq.setScope(CategoryScope.GLOBAL);
        globalReq.setDescription("VIP Attendees");

        PassCategoryResponse globalRes = passCategoryService.createCategory(globalReq);
        assertThat(globalRes.getId()).isNotNull();
        assertThat(globalRes.getScope()).isEqualTo(CategoryScope.GLOBAL);

        CreatePassCategoryRequest eventReq = new CreatePassCategoryRequest();
        eventReq.setName("Hackathon Participant");
        eventReq.setCode("HACK_" + UUID.randomUUID().toString().substring(0, 4));
        eventReq.setScope(CategoryScope.EVENT_SPECIFIC);
        eventReq.setEventId(testEvent.getId());
        eventReq.setDescription("Only for this event");

        PassCategoryResponse eventRes = passCategoryService.createCategory(eventReq);
        assertThat(eventRes.getId()).isNotNull();
        assertThat(eventRes.getScope()).isEqualTo(CategoryScope.EVENT_SPECIFIC);
        assertThat(eventRes.getEventId()).isEqualTo(testEvent.getId());
    }

    @Test
    @DisplayName("Reject invalid policy: NO_QR_WALK_IN with QR required")
    void testRejectInvalidPolicyCombinations() {
        com.app.eventmanagmentsystem.model.PassCategory globalCat = passCategoryRepository.findByCode("GUEST")
                .orElseGet(() -> {
                    var pc = new com.app.eventmanagmentsystem.model.PassCategory();
                    pc.setName("Guest");
                    pc.setCode("GUEST");
                    pc.setScope(CategoryScope.GLOBAL);
                    pc.setActive(true);
                    return passCategoryRepository.save(pc);
                });

        ConfigureEventCategoryRequest configReq = new ConfigureEventCategoryRequest();
        configReq.setCategoryId(globalCat.getId());
        configReq.setActivationMode(ActivationMode.NO_QR_WALK_IN);
        configReq.setQrRequired(true); // Invalid! Walk-in must not require QR
        configReq.setIdentityPolicy(IdentityPolicy.COLLECT_IDENTITY);
        configReq.setEnabled(true);

        assertThatThrownBy(() -> eventPassCategoryService.configureCategoryForEvent(testEvent.getId(), configReq))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cannot have QR generation enabled");
    }

    @Test
    @DisplayName("Save draft fields, publish form schema version snapshot")
    void testFormBuilderPublishing() {
        com.app.eventmanagmentsystem.model.PassCategory cat = passCategoryRepository.findByCode("VIP")
                .orElseGet(() -> {
                    var pc = new com.app.eventmanagmentsystem.model.PassCategory();
                    pc.setName("VIP");
                    pc.setCode("VIP");
                    pc.setScope(CategoryScope.GLOBAL);
                    pc.setActive(true);
                    return passCategoryRepository.save(pc);
                });

        ConfigureEventCategoryRequest configReq = new ConfigureEventCategoryRequest();
        configReq.setCategoryId(cat.getId());
        configReq.setActivationMode(ActivationMode.SELF_SERVICE);
        configReq.setQrRequired(true);
        configReq.setIdentityPolicy(IdentityPolicy.COLLECT_IDENTITY);
        configReq.setEnabled(true);

        EventPassCategoryResponse epc = eventPassCategoryService.configureCategoryForEvent(testEvent.getId(), configReq);

        List<FormFieldDto> fields = List.of(
                FormFieldDto.builder()
                        .fieldKey("company")
                        .label("Company Name")
                        .fieldType(FieldType.TEXT)
                        .required(true)
                        .visible(true)
                        .displayOrder(1)
                        .active(true)
                        .build(),
                FormFieldDto.builder()
                        .fieldKey("dietary")
                        .label("Dietary Restrictions")
                        .fieldType(FieldType.SELECT)
                        .options("None,Vegetarian,Vegan,Halal")
                        .required(false)
                        .visible(true)
                        .displayOrder(2)
                        .active(true)
                        .build()
        );

        formBuilderService.saveDraftFields(epc.getId(), fields);

        PublishFormVersionResponse pubRes = formBuilderService.publishFormVersion(epc.getId(), "Initial release");
        assertThat(pubRes.getVersionNumber()).isEqualTo(1);
        assertThat(pubRes.getFieldCount()).isEqualTo(2);

        FormSchemaResponse schemaRes = formBuilderService.getPublishedFormSchema(epc.getId());
        assertThat(schemaRes.getVersionNumber()).isEqualTo(1);
        assertThat(schemaRes.getFields()).hasSize(2);
        assertThat(schemaRes.getFields().get(0).getFieldKey()).isEqualTo("company");
    }

    @Test
    @DisplayName("SCAN_AUTO_ACTIVATE + NO_IDENTITY: scan activates atomically without attendee form")
    void testScanAutoActivateNoIdentity() {
        com.app.eventmanagmentsystem.model.PassCategory autoCat = passCategoryRepository.findByCode("AUTO_SCAN")
                .orElseGet(() -> {
                    var pc = new com.app.eventmanagmentsystem.model.PassCategory();
                    pc.setName("Auto Scan Pass");
                    pc.setCode("AUTO_SCAN");
                    pc.setScope(CategoryScope.GLOBAL);
                    pc.setActive(true);
                    return passCategoryRepository.save(pc);
                });

        ConfigureEventCategoryRequest configReq = new ConfigureEventCategoryRequest();
        configReq.setCategoryId(autoCat.getId());
        configReq.setActivationMode(ActivationMode.SCAN_AUTO_ACTIVATE);
        configReq.setQrRequired(true);
        configReq.setIdentityPolicy(IdentityPolicy.NO_IDENTITY);
        configReq.setEnabled(true);

        EventPassCategoryResponse epc = eventPassCategoryService.configureCategoryForEvent(testEvent.getId(), configReq);

        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(testEvent.getId());
        genReq.setEventCategoryId(epc.getId());
        genReq.setBatchSize(1);

        List<PassResponse> passes = passGenerationService.generatePasses(genReq);
        assertThat(passes).hasSize(1);
        PassResponse pass = passes.get(0);
        assertThat(pass.getRawToken()).isNotNull();
        assertThat(pass.getStatus()).isEqualTo(PassStatus.NOT_ACTIVATED);

        // Auto-activate scan
        AutoActivatePassRequest autoReq = new AutoActivatePassRequest();
        autoReq.setRawToken(pass.getRawToken());
        autoReq.setEventId(testEvent.getId());

        PassResponse activatedPass = passActivationService.autoActivatePass(autoReq);
        assertThat(activatedPass.getStatus()).isEqualTo(PassStatus.ACTIVATED);
        assertThat(activatedPass.getAttendeeName()).isEqualTo("No Identity (Auto-Activated)");

        // Gate check-in with NO_IDENTITY pass succeeds
        CheckInRequest checkInReq = new CheckInRequest();
        checkInReq.setRawToken(pass.getRawToken());
        checkInReq.setEventId(testEvent.getId());
        checkInReq.setEventDayId(testDay.getId());

        CheckInResponse checkInRes = checkInService.performCheckIn(checkInReq);
        assertThat(checkInRes.getCheckInId()).isNotNull();
        assertThat(checkInRes.getAttendeeName()).isEqualTo("No Identity (Auto-Activated)");
    }

    @Test
    @DisplayName("Staff walk-in admission (NO_QR_WALK_IN): single-action pass generation, admission, and check-in")
    void testStaffWalkInAdmission() {
        com.app.eventmanagmentsystem.model.PassCategory walkInCat = passCategoryRepository.findByCode("WALKIN")
                .orElseGet(() -> {
                    var pc = new com.app.eventmanagmentsystem.model.PassCategory();
                    pc.setName("Staff Walk-In");
                    pc.setCode("WALKIN");
                    pc.setScope(CategoryScope.GLOBAL);
                    pc.setActive(true);
                    return passCategoryRepository.save(pc);
                });

        ConfigureEventCategoryRequest configReq = new ConfigureEventCategoryRequest();
        configReq.setCategoryId(walkInCat.getId());
        configReq.setActivationMode(ActivationMode.NO_QR_WALK_IN);
        configReq.setQrRequired(false);
        configReq.setIdentityPolicy(IdentityPolicy.COLLECT_IDENTITY);
        configReq.setEnabled(true);

        EventPassCategoryResponse epc = eventPassCategoryService.configureCategoryForEvent(testEvent.getId(), configReq);

        WalkInAdmitRequest admitReq = new WalkInAdmitRequest();
        admitReq.setEventId(testEvent.getId());
        admitReq.setEventDayId(testDay.getId());
        admitReq.setEventCategoryId(epc.getId());
        admitReq.setFullName("John WalkIn");
        admitReq.setEmail("john.walkin@example.com");
        admitReq.setOrganization("Acme Corp");

        WalkInAdmitResponse admitRes = walkInAdmissionService.admitAttendee(admitReq);
        assertThat(admitRes.getCheckInId()).isNotNull();
        assertThat(admitRes.getPassId()).isNotNull();
        assertThat(admitRes.getAttendeeName()).isEqualTo("John WalkIn");
        assertThat(admitRes.getCategoryCode()).isEqualTo("WALKIN");

        // Verify pass is ACTIVATED with null tokenHash
        EventPass pass = eventPassRepository.findById(admitRes.getPassId()).orElseThrow();
        assertThat(pass.getStatus()).isEqualTo(PassStatus.ACTIVATED);
        assertThat(pass.getTokenHash()).isNull();
        assertThat(pass.getAttendee().getFullName()).isEqualTo("John WalkIn");
    }
}
