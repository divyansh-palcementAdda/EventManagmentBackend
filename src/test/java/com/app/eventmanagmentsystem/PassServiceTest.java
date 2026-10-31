package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.exceptions.*;
import com.app.eventmanagmentsystem.model.Attendee;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.enums.ActivationPolicy;
import com.app.eventmanagmentsystem.model.enums.CheckInPolicy;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.pass.ActivatePassRequest;
import com.app.eventmanagmentsystem.payload.pass.GeneratePassRequest;
import com.app.eventmanagmentsystem.payload.pass.PassResponse;
import com.app.eventmanagmentsystem.repository.AttendeeRepository;
import com.app.eventmanagmentsystem.repository.EmailOutboxRepository;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.service.impl.PassActivationService;
import com.app.eventmanagmentsystem.service.impl.PassGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PassServiceTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventPassRepository eventPassRepository;

    @Autowired
    private AttendeeRepository attendeeRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private PassGenerationService passGenerationService;

    @Autowired
    private PassActivationService passActivationService;

    private Event testEvent;

    @BeforeEach
    void setUp() {
        testEvent = new Event();
        testEvent.setName("Tech Symposium 2026");
        testEvent.setEventCode("TS26");
        testEvent.setPassSeriesCode("F5EE57F3");
        testEvent.setStatus(EventStatus.ACTIVE);
        testEvent.setActivationPolicy(ActivationPolicy.SELF_SERVICE);
        testEvent.setCheckInPolicy(CheckInPolicy.SINGLE_ENTRY);
        testEvent.setTimezone("UTC");
        testEvent.setMaximumPasses(100);
        testEvent = eventRepository.save(testEvent);
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("Pass generation: format conforms to {EVENT_CODE}-{SERIES_CODE}-{SEQUENCE}")
    void passNumberFormatMatchesSpecification() {
        GeneratePassRequest req = new GeneratePassRequest();
        req.setEventId(testEvent.getId());
        req.setBatchSize(1);

        List<PassResponse> passes = passGenerationService.generatePasses(req);
        assertThat(passes).hasSize(1);

        PassResponse pass = passes.get(0);
        assertThat(pass.getPassNumber()).isEqualTo("TS26-F5EE57F3-0001");
        assertThat(pass.getStatus()).isEqualTo(PassStatus.NOT_ACTIVATED);
        assertThat(pass.getRawToken()).isNotNull(); // Raw token given at generation time
        assertThat(pass.getRawToken()).hasSize(64);
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("Pass generation: sequential numbering and batch creation")
    void sequentialNumberingInBatch() {
        GeneratePassRequest req = new GeneratePassRequest();
        req.setEventId(testEvent.getId());
        req.setBatchSize(3);

        List<PassResponse> passes = passGenerationService.generatePasses(req);
        assertThat(passes).hasSize(3);
        assertThat(passes.get(0).getPassNumber()).isEqualTo("TS26-F5EE57F3-0001");
        assertThat(passes.get(1).getPassNumber()).isEqualTo("TS26-F5EE57F3-0002");
        assertThat(passes.get(2).getPassNumber()).isEqualTo("TS26-F5EE57F3-0003");
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("Pass generation: raw token is NOT stored in DB, only token_hash")
    void rawTokenNotStoredInDatabase() {
        GeneratePassRequest req = new GeneratePassRequest();
        req.setEventId(testEvent.getId());
        req.setBatchSize(1);

        List<PassResponse> passes = passGenerationService.generatePasses(req);
        String rawToken = passes.get(0).getRawToken();
        UUID passId = passes.get(0).getId();

        EventPass savedPass = eventPassRepository.findById(passId).orElseThrow();
        assertThat(savedPass.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(savedPass.getTokenHash()).hasSize(64);
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("Pass generation: idempotency key returns existing pass without generating new sequence")
    void idempotencyKeyReturnsExistingPass() {
        String idempotencyKey = "idemp-req-12345";
        GeneratePassRequest req1 = new GeneratePassRequest();
        req1.setEventId(testEvent.getId());
        req1.setBatchSize(1);
        req1.setIdempotencyKey(idempotencyKey);

        List<PassResponse> passes1 = passGenerationService.generatePasses(req1);
        assertThat(passes1).hasSize(1);

        GeneratePassRequest req2 = new GeneratePassRequest();
        req2.setEventId(testEvent.getId());
        req2.setBatchSize(1);
        req2.setIdempotencyKey(idempotencyKey);

        List<PassResponse> passes2 = passGenerationService.generatePasses(req2);
        assertThat(passes2).hasSize(1);
        assertThat(passes2.get(0).getPassNumber()).isEqualTo(passes1.get(0).getPassNumber());
        assertThat(passes2.get(0).getId()).isEqualTo(passes1.get(0).getId());
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("Pass generation: maximum passes limit rejection")
    void maximumPassesLimitRejection() {
        testEvent.setMaximumPasses(2);
        eventRepository.save(testEvent);

        GeneratePassRequest req = new GeneratePassRequest();
        req.setEventId(testEvent.getId());
        req.setBatchSize(3);

        assertThatThrownBy(() -> passGenerationService.generatePasses(req))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("exceed the maximum limit");
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("Pass activation: successful activation transitions state and enqueues outbox email")
    void successfulActivationWorkflow() {
        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(testEvent.getId());
        genReq.setBatchSize(1);
        PassResponse pass = passGenerationService.generatePasses(genReq).get(0);

        ActivatePassRequest actReq = new ActivatePassRequest();
        actReq.setEventId(testEvent.getId());
        actReq.setRawToken(pass.getRawToken());
        actReq.setAttendeeFullName("Jane Doe");
        actReq.setAttendeeEmail("jane.doe@example.com");
        actReq.setAttendeeCollege("MIT");
        actReq.setAttendeeCourse("Computer Science");
        actReq.setAttendeeOrganization("Acme Inc");

        PassResponse activated = passActivationService.activatePass(actReq);
        assertThat(activated.getStatus()).isEqualTo(PassStatus.ACTIVATED);
        assertThat(activated.getActivatedAt()).isNotNull();
        assertThat(activated.getRawToken()).isNull(); // Token stripped on activation response

        // Verify Attendee record
        assertThat(attendeeRepository.existsByEventAndEmail(testEvent.getId(), "jane.doe@example.com")).isTrue();

        // Verify EmailOutbox record enqueued in same transaction
        assertThat(emailOutboxRepository.findAll()).isNotEmpty();
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("Pass activation: invalid QR token is rejected")
    void invalidQrTokenRejected() {
        ActivatePassRequest actReq = new ActivatePassRequest();
        actReq.setEventId(testEvent.getId());
        actReq.setRawToken("NON_EXISTENT_INVALID_TOKEN_1234567890ABCDEF1234567890ABCDEF");
        actReq.setAttendeeFullName("Jane Doe");
        actReq.setAttendeeEmail("jane.doe@example.com");

        assertThatThrownBy(() -> passActivationService.activatePass(actReq))
                .isInstanceOf(InvalidQrTokenException.class);
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("Pass activation: already-activated pass is rejected")
    void alreadyActivatedPassRejected() {
        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(testEvent.getId());
        genReq.setBatchSize(1);
        PassResponse pass = passGenerationService.generatePasses(genReq).get(0);

        ActivatePassRequest actReq = new ActivatePassRequest();
        actReq.setEventId(testEvent.getId());
        actReq.setRawToken(pass.getRawToken());
        actReq.setAttendeeFullName("Jane Doe");
        actReq.setAttendeeEmail("jane.doe@example.com");
        actReq.setAttendeeCollege("MIT");
        actReq.setAttendeeCourse("Computer Science");

        passActivationService.activatePass(actReq);

        // Attempt second activation
        ActivatePassRequest actReq2 = new ActivatePassRequest();
        actReq2.setEventId(testEvent.getId());
        actReq2.setRawToken(pass.getRawToken());
        actReq2.setAttendeeFullName("Second Person");
        actReq2.setAttendeeEmail("second@example.com");

        assertThatThrownBy(() -> passActivationService.activatePass(actReq2))
                .isInstanceOf(PassAlreadyActivatedException.class);
    }

    @Test
    @WithMockUser(authorities = {"PASS_GENERATE", "PASS_REVOKE"})
    @DisplayName("Pass activation: revoked pass cannot be activated")
    void revokedPassCannotBeActivated() {
        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(testEvent.getId());
        genReq.setBatchSize(1);
        PassResponse pass = passGenerationService.generatePasses(genReq).get(0);

        passActivationService.revokePass(pass.getId(), "Security breach", "ADMIN_ID", "Admin");

        ActivatePassRequest actReq = new ActivatePassRequest();
        actReq.setEventId(testEvent.getId());
        actReq.setRawToken(pass.getRawToken());
        actReq.setAttendeeFullName("Jane Doe");
        actReq.setAttendeeEmail("jane.doe@example.com");

        assertThatThrownBy(() -> passActivationService.activatePass(actReq))
                .isInstanceOf(PassRevokedException.class);
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("Pass activation: duplicate attendee email for same event is prevented")
    void duplicateAttendeeEmailForSameEventPrevented() {
        GeneratePassRequest genReq = new GeneratePassRequest();
        genReq.setEventId(testEvent.getId());
        genReq.setBatchSize(2);
        List<PassResponse> passes = passGenerationService.generatePasses(genReq);

        ActivatePassRequest actReq1 = new ActivatePassRequest();
        actReq1.setEventId(testEvent.getId());
        actReq1.setRawToken(passes.get(0).getRawToken());
        actReq1.setAttendeeFullName("Jane Doe");
        actReq1.setAttendeeEmail("jane.doe@example.com");
        actReq1.setAttendeeCollege("MIT");
        actReq1.setAttendeeCourse("Computer Science");
        passActivationService.activatePass(actReq1);

        ActivatePassRequest actReq2 = new ActivatePassRequest();
        actReq2.setEventId(testEvent.getId());
        actReq2.setRawToken(passes.get(1).getRawToken());
        actReq2.setAttendeeFullName("Another Jane");
        actReq2.setAttendeeEmail("jane.doe@example.com"); // Duplicate email!
        actReq2.setAttendeeCollege("MIT");
        actReq2.setAttendeeCourse("Computer Science");

        assertThatThrownBy(() -> passActivationService.activatePass(actReq2))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @WithMockUser(authorities = "PASS_GENERATE")
    @DisplayName("Pass activation: same attendee email can register for distinct events on separate passes")
    void multiEventRegistrationForSameAttendeeAllowed() {
        // Event 1 pass
        GeneratePassRequest genReq1 = new GeneratePassRequest();
        genReq1.setEventId(testEvent.getId());
        genReq1.setBatchSize(1);
        PassResponse pass1 = passGenerationService.generatePasses(genReq1).get(0);

        ActivatePassRequest act1 = new ActivatePassRequest();
        act1.setEventId(testEvent.getId());
        act1.setRawToken(pass1.getRawToken());
        act1.setAttendeeFullName("Sam Multiverse");
        act1.setAttendeeEmail("sam@multiverse.test");
        act1.setAttendeeCollege("Caltech");
        act1.setAttendeeCourse("Physics");
        PassResponse res1 = passActivationService.activatePass(act1);
        assertThat(res1.getStatus()).isEqualTo(PassStatus.ACTIVATED);

        // Event 2 pass (separate event)
        Event event2 = new Event();
        event2.setName("AI Summit");
        event2.setEventCode("AISUMMIT");
        event2.setPassSeriesCode("AIS26");
        event2.setStartsAt(Instant.now().plusSeconds(7200));
        event2.setEndsAt(Instant.now().plusSeconds(14400));
        event2.setVenue("Convention Hall B");
        event2.setStatus(EventStatus.PUBLISHED);
        Event savedEvent2 = eventRepository.saveAndFlush(event2);

        GeneratePassRequest genReq2 = new GeneratePassRequest();
        genReq2.setEventId(savedEvent2.getId());
        genReq2.setBatchSize(1);
        genReq2.setCategory(com.app.eventmanagmentsystem.model.enums.PassCategory.OTHER);
        PassResponse pass2 = passGenerationService.generatePasses(genReq2).get(0);

        ActivatePassRequest act2 = new ActivatePassRequest();
        act2.setEventId(savedEvent2.getId());
        act2.setRawToken(pass2.getRawToken());
        act2.setAttendeeFullName("Sam Multiverse");
        act2.setAttendeeEmail("sam@multiverse.test");
        act2.setAttendeeOrganization("OpenAI Research");
        act2.setAttendeeDesignation("Research Scientist");
        PassResponse res2 = passActivationService.activatePass(act2);

        assertThat(res2.getStatus()).isEqualTo(PassStatus.ACTIVATED);
        assertThat(res2.getCategory()).isEqualTo(com.app.eventmanagmentsystem.model.enums.PassCategory.OTHER);

        // Confirm both attendees exist in their respective event scopes
        assertThat(attendeeRepository.existsByEventAndEmail(testEvent.getId(), "sam@multiverse.test")).isTrue();
        assertThat(attendeeRepository.existsByEventAndEmail(savedEvent2.getId(), "sam@multiverse.test")).isTrue();
    }
}
