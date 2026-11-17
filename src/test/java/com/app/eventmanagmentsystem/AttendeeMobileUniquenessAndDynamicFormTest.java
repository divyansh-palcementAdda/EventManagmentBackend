package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.exceptions.DuplicateResourceException;
import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventPassCategory;
import com.app.eventmanagmentsystem.model.enums.ActivationMode;
import com.app.eventmanagmentsystem.model.enums.ActivationPolicy;
import com.app.eventmanagmentsystem.model.enums.CheckInPolicy;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.model.enums.FieldType;
import com.app.eventmanagmentsystem.model.enums.IdentityPolicy;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.category.FormFieldDto;
import com.app.eventmanagmentsystem.payload.pass.ActivatePassRequest;
import com.app.eventmanagmentsystem.payload.pass.GeneratePassRequest;
import com.app.eventmanagmentsystem.payload.pass.PassResponse;
import com.app.eventmanagmentsystem.repository.AttendeeRepository;
import com.app.eventmanagmentsystem.repository.EventPassCategoryRepository;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.repository.PassCategoryRepository;
import com.app.eventmanagmentsystem.service.impl.FormBuilderService;
import com.app.eventmanagmentsystem.service.impl.PassActivationService;
import com.app.eventmanagmentsystem.service.impl.PassGenerationService;
import com.app.eventmanagmentsystem.util.PhoneNumberUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AttendeeMobileUniquenessAndDynamicFormTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventPassRepository eventPassRepository;

    @Autowired
    private PassCategoryRepository passCategoryRepository;

    @Autowired
    private EventPassCategoryRepository eventPassCategoryRepository;

    @Autowired
    private AttendeeRepository attendeeRepository;

    @Autowired
    private PassGenerationService passGenerationService;

    @Autowired
    private PassActivationService passActivationService;

    @Autowired
    private FormBuilderService formBuilderService;

    private Event eventA;
    private Event eventB;
    private com.app.eventmanagmentsystem.model.PassCategory studentCategory;
    private com.app.eventmanagmentsystem.model.PassCategory guestCategory;
    private EventPassCategory epcStudentEventA;
    private EventPassCategory epcGuestEventA;

    @BeforeEach
    void setUp() {
        // Create Event A
        eventA = new Event();
        eventA.setName("Event Alpha");
        eventA.setEventCode("EVTA");
        eventA.setPassSeriesCode("AAAA");
        eventA.setStatus(EventStatus.ACTIVE);
        eventA.setActivationPolicy(ActivationPolicy.SELF_SERVICE);
        eventA.setCheckInPolicy(CheckInPolicy.SINGLE_ENTRY);
        eventA.setTimezone("UTC");
        eventA.setMaximumPasses(200);
        eventA = eventRepository.save(eventA);

        // Create Event B
        eventB = new Event();
        eventB.setName("Event Beta");
        eventB.setEventCode("EVTB");
        eventB.setPassSeriesCode("BBBB");
        eventB.setStatus(EventStatus.ACTIVE);
        eventB.setActivationPolicy(ActivationPolicy.SELF_SERVICE);
        eventB.setCheckInPolicy(CheckInPolicy.SINGLE_ENTRY);
        eventB.setTimezone("UTC");
        eventB.setMaximumPasses(200);
        eventB = eventRepository.save(eventB);

        // Find or create STUDENT category entity
        studentCategory = passCategoryRepository.findByCode("STUDENT")
                .orElseGet(() -> {
                    var pc = new com.app.eventmanagmentsystem.model.PassCategory();
                    pc.setCode("STUDENT");
                    pc.setName("Student Pass");
                    pc.setActive(true);
                    return passCategoryRepository.save(pc);
                });

        // Find or create GUEST category entity
        guestCategory = passCategoryRepository.findByCode("GUEST")
                .orElseGet(() -> {
                    var pc = new com.app.eventmanagmentsystem.model.PassCategory();
                    pc.setCode("GUEST");
                    pc.setName("Guest Pass");
                    pc.setActive(true);
                    return passCategoryRepository.save(pc);
                });

        // Link categories to Event A
        epcStudentEventA = new EventPassCategory();
        epcStudentEventA.setEvent(eventA);
        epcStudentEventA.setPassCategory(studentCategory);
        epcStudentEventA.setActive(true);
        epcStudentEventA.setActivationMode(ActivationMode.SELF_SERVICE);
        epcStudentEventA.setIdentityPolicy(IdentityPolicy.COLLECT_IDENTITY);
        epcStudentEventA = eventPassCategoryRepository.save(epcStudentEventA);

        epcGuestEventA = new EventPassCategory();
        epcGuestEventA.setEvent(eventA);
        epcGuestEventA.setPassCategory(guestCategory);
        epcGuestEventA.setActive(true);
        epcGuestEventA.setActivationMode(ActivationMode.SELF_SERVICE);
        epcGuestEventA.setIdentityPolicy(IdentityPolicy.COLLECT_IDENTITY);
        epcGuestEventA = eventPassCategoryRepository.save(epcGuestEventA);
    }

    @Test
    @DisplayName("PhoneNumberUtil normalizes Indian and international phone numbers accurately")
    void testPhoneNumberNormalization() {
        assertThat(PhoneNumberUtil.normalize("9876543210")).isEqualTo("+919876543210");
        assertThat(PhoneNumberUtil.normalize("+91 9876543210")).isEqualTo("+919876543210");
        assertThat(PhoneNumberUtil.normalize("+91-98765-43210")).isEqualTo("+919876543210");
        assertThat(PhoneNumberUtil.normalize("09876543210")).isEqualTo("+919876543210");
        assertThat(PhoneNumberUtil.normalize("+1 (555) 123-4567")).isEqualTo("+15551234567");
        assertThat(PhoneNumberUtil.normalize(null)).isNull();
        assertThat(PhoneNumberUtil.normalize("")).isNull();
        assertThat(PhoneNumberUtil.isValid("9876543210")).isTrue();
        assertThat(PhoneNumberUtil.isValid("123")).isFalse();
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("Issue 1: Enforce one mobile number per attendee per event - duplicate rejected")
    void testMobileNumberUniquenessPerEventDuplicateRejected() {
        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(eventA.getId());
        genReq.setBatchSize(2);
        List<PassResponse> passes = passGenerationService.generatePasses(genReq);

        // Attendee 1 activates with mobile 9876543210
        ActivatePassRequest act1 = new ActivatePassRequest();
        act1.setEventId(eventA.getId());
        act1.setRawToken(passes.get(0).getRawToken());
        act1.setAttendeeFullName("Divyansh Tomar");
        act1.setAttendeeEmail("divyansh@example.com");
        act1.setAttendeeMobileNumber("9876543210");
        act1.setAttendeeCollege("Medicaps");
        act1.setAttendeeCourse("B.Tech CSE");
        PassResponse res1 = passActivationService.activatePass(act1);
        assertThat(res1.getStatus()).isEqualTo(PassStatus.ACTIVATED);

        // Attendee 2 tries to activate with same mobile in different format (+91 9876543210)
        ActivatePassRequest act2 = new ActivatePassRequest();
        act2.setEventId(eventA.getId());
        act2.setRawToken(passes.get(1).getRawToken());
        act2.setAttendeeFullName("Rahul Verma");
        act2.setAttendeeEmail("rahul@example.com");
        act2.setAttendeeMobileNumber("+91 9876543210");
        act2.setAttendeeCollege("Medicaps");
        act2.setAttendeeCourse("B.Tech CSE");

        assertThatThrownBy(() -> passActivationService.activatePass(act2))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("This mobile number is already registered for this event. Please use the registered attendee's existing pass or contact the event organizer.");
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("Issue 1: Same mobile number CAN be used across different events")
    void testSameMobileNumberAcrossDifferentEventsAllowed() {
        // Generate pass in Event A
        GeneratePassRequest genReqA = new GeneratePassRequest();
        genReqA.setEventId(eventA.getId());
        genReqA.setBatchSize(1);
        PassResponse passA = passGenerationService.generatePasses(genReqA).get(0);

        // Generate pass in Event B
        GeneratePassRequest genReqB = new GeneratePassRequest();
        genReqB.setEventId(eventB.getId());
        genReqB.setBatchSize(1);
        PassResponse passB = passGenerationService.generatePasses(genReqB).get(0);

        // Activate in Event A with mobile 9876543210
        ActivatePassRequest actA = new ActivatePassRequest();
        actA.setEventId(eventA.getId());
        actA.setRawToken(passA.getRawToken());
        actA.setAttendeeFullName("Divyansh Tomar");
        actA.setAttendeeEmail("divyansh@example.com");
        actA.setAttendeeMobileNumber("9876543210");
        actA.setAttendeeCollege("Medicaps");
        actA.setAttendeeCourse("B.Tech CSE");
        PassResponse resA = passActivationService.activatePass(actA);
        assertThat(resA.getStatus()).isEqualTo(PassStatus.ACTIVATED);

        // Activate in Event B with same mobile 9876543210 -> Must succeed!
        ActivatePassRequest actB = new ActivatePassRequest();
        actB.setEventId(eventB.getId());
        actB.setRawToken(passB.getRawToken());
        actB.setAttendeeFullName("Divyansh Tomar");
        actB.setAttendeeEmail("divyansh.eventb@example.com");
        actB.setAttendeeMobileNumber("+91 9876543210");
        actB.setAttendeeCollege("Medicaps");
        actB.setAttendeeCourse("B.Tech CSE");
        PassResponse resB = passActivationService.activatePass(actB);
        assertThat(resB.getStatus()).isEqualTo(PassStatus.ACTIVATED);

        assertThat(attendeeRepository.existsByEventIdAndNormalizedMobileNumber(eventA.getId(), "+919876543210")).isTrue();
        assertThat(attendeeRepository.existsByEventIdAndNormalizedMobileNumber(eventB.getId(), "+919876543210")).isTrue();
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("Issue 1: Email uniqueness restriction removed - multiple attendees can share same email with distinct mobiles")
    void testMultipleAttendeesCanShareSameEmailWithDifferentMobiles() {
        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(eventA.getId());
        genReq.setBatchSize(2);
        List<PassResponse> passes = passGenerationService.generatePasses(genReq);

        // Attendee 1
        ActivatePassRequest act1 = new ActivatePassRequest();
        act1.setEventId(eventA.getId());
        act1.setRawToken(passes.get(0).getRawToken());
        act1.setAttendeeFullName("Parent Person");
        act1.setAttendeeEmail("family@example.com");
        act1.setAttendeeMobileNumber("9876543211");
        act1.setAttendeeCollege("College");
        act1.setAttendeeCourse("Course");
        PassResponse res1 = passActivationService.activatePass(act1);
        assertThat(res1.getStatus()).isEqualTo(PassStatus.ACTIVATED);

        // Attendee 2 with SAME email, different mobile
        ActivatePassRequest act2 = new ActivatePassRequest();
        act2.setEventId(eventA.getId());
        act2.setRawToken(passes.get(1).getRawToken());
        act2.setAttendeeFullName("Child Person");
        act2.setAttendeeEmail("family@example.com");
        act2.setAttendeeMobileNumber("9876543212");
        act2.setAttendeeCollege("College");
        act2.setAttendeeCourse("Course");
        PassResponse res2 = passActivationService.activatePass(act2);
        assertThat(res2.getStatus()).isEqualTo(PassStatus.ACTIVATED);
    }

    @Test
    @WithMockUser(authorities = {"REGISTRATION_FORM_UPDATE", "REGISTRATION_FORM_PUBLISH", "PASS_GENERATE", "SUPER_ADMIN"})
    @DisplayName("Issue 2: Student pass with dynamic schema requiring Course - missing course rejected with field error")
    void testStudentPassWithRequiredCourseDynamicSchemaRejectedWhenMissing() {
        // Save and publish schema for Student category with Course as required
        List<FormFieldDto> fields = List.of(
                FormFieldDto.builder()
                        .fieldKey("course")
                        .label("Course or Program")
                        .fieldType(FieldType.TEXT)
                        .required(true)
                        .visible(true)
                        .displayOrder(1)
                        .active(true)
                        .build()
        );
        formBuilderService.saveDraftFields(epcStudentEventA.getId(), fields);
        formBuilderService.publishFormVersion(epcStudentEventA.getId(), "v1 with required course");

        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(eventA.getId());
        genReq.setBatchSize(1);
        genReq.setCategory(com.app.eventmanagmentsystem.model.enums.PassCategory.STUDENT);
        PassResponse pass = passGenerationService.generatePasses(genReq).get(0);

        ActivatePassRequest actReq = new ActivatePassRequest();
        actReq.setEventId(eventA.getId());
        actReq.setRawToken(pass.getRawToken());
        actReq.setAttendeeFullName("Test Student");
        actReq.setAttendeeEmail("student@example.com");
        actReq.setAttendeeMobileNumber("9876543215");
        // No course provided!

        assertThatThrownBy(() -> passActivationService.activatePass(actReq))
                .isInstanceOf(InvalidRequestException.class)
                .satisfies(ex -> {
                    InvalidRequestException ire = (InvalidRequestException) ex;
                    assertThat(ire.getFieldErrors()).containsKey("course");
                    assertThat(ire.getFieldErrors().get("course")).contains("is required");
                });
    }

    @Test
    @WithMockUser(authorities = {"REGISTRATION_FORM_UPDATE", "REGISTRATION_FORM_PUBLISH", "PASS_GENERATE", "SUPER_ADMIN"})
    @DisplayName("Issue 2: Student pass with dynamic schema requiring Course - valid Course is accepted")
    void testStudentPassWithRequiredCourseDynamicSchemaAcceptedWhenProvided() {
        List<FormFieldDto> fields = List.of(
                FormFieldDto.builder()
                        .fieldKey("course")
                        .label("Course or Program")
                        .fieldType(FieldType.TEXT)
                        .required(true)
                        .visible(true)
                        .displayOrder(1)
                        .active(true)
                        .build()
        );
        formBuilderService.saveDraftFields(epcStudentEventA.getId(), fields);
        formBuilderService.publishFormVersion(epcStudentEventA.getId(), "v1 with required course");

        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(eventA.getId());
        genReq.setBatchSize(1);
        genReq.setCategory(com.app.eventmanagmentsystem.model.enums.PassCategory.STUDENT);
        PassResponse pass = passGenerationService.generatePasses(genReq).get(0);

        ActivatePassRequest actReq = new ActivatePassRequest();
        actReq.setEventId(eventA.getId());
        actReq.setRawToken(pass.getRawToken());
        actReq.setAttendeeFullName("Test Student");
        actReq.setAttendeeEmail("student.pass@example.com");
        actReq.setAttendeeMobileNumber("9876543216");
        actReq.setAttendeeCourse("M.Sc Artificial Intelligence");

        PassResponse res = passActivationService.activatePass(actReq);
        assertThat(res.getStatus()).isEqualTo(PassStatus.ACTIVATED);
    }

    @Test
    @WithMockUser(authorities = {"REGISTRATION_FORM_UPDATE", "REGISTRATION_FORM_PUBLISH", "PASS_GENERATE", "SUPER_ADMIN"})
    @DisplayName("Issue 2: Dynamic schema is source of truth - Student pass where Course is NOT configured succeeds without Course")
    void testStudentPassWithoutCourseInSchemaSucceedsWithoutCourse() {
        // Publish schema for Student category WITHOUT Course field (e.g. only roll number)
        List<FormFieldDto> fields = List.of(
                FormFieldDto.builder()
                        .fieldKey("rollNumber")
                        .label("Roll Number")
                        .fieldType(FieldType.TEXT)
                        .required(true)
                        .visible(true)
                        .displayOrder(1)
                        .active(true)
                        .build()
        );
        formBuilderService.saveDraftFields(epcStudentEventA.getId(), fields);
        formBuilderService.publishFormVersion(epcStudentEventA.getId(), "v1 without course");

        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(eventA.getId());
        genReq.setBatchSize(1);
        genReq.setCategory(com.app.eventmanagmentsystem.model.enums.PassCategory.STUDENT);
        PassResponse pass = passGenerationService.generatePasses(genReq).get(0);

        ActivatePassRequest actReq = new ActivatePassRequest();
        actReq.setEventId(eventA.getId());
        actReq.setRawToken(pass.getRawToken());
        actReq.setAttendeeFullName("Test Student Minimal");
        actReq.setAttendeeEmail("student.minimal@example.com");
        actReq.setAttendeeMobileNumber("9876543217");
        actReq.setAnswers(Map.of("rollNumber", "RN-10023"));
        // Notice: NO course provided, and it should SUCCEED!

        PassResponse res = passActivationService.activatePass(actReq);
        assertThat(res.getStatus()).isEqualTo(PassStatus.ACTIVATED);
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("Issue 2: Non-Student category (GUEST/VIP) activates successfully without Course")
    void testGuestCategoryActivatesWithoutCourse() {
        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(eventA.getId());
        genReq.setBatchSize(1);
        genReq.setCategory(com.app.eventmanagmentsystem.model.enums.PassCategory.OTHER);
        PassResponse pass = passGenerationService.generatePasses(genReq).get(0);

        ActivatePassRequest actReq = new ActivatePassRequest();
        actReq.setEventId(eventA.getId());
        actReq.setRawToken(pass.getRawToken());
        actReq.setAttendeeFullName("Honorable Guest");
        actReq.setAttendeeEmail("guest@example.com");
        actReq.setAttendeeMobileNumber("9876543218");
        actReq.setAttendeeOrganization("Tech Ventures");
        actReq.setAttendeeDesignation("Partner");
        // No course!

        PassResponse res = passActivationService.activatePass(actReq);
        assertThat(res.getStatus()).isEqualTo(PassStatus.ACTIVATED);
    }
}
