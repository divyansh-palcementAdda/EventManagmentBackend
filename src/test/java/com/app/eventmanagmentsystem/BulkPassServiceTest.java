package com.app.eventmanagmentsystem;

import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.model.Attendee;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.enums.ActivationPolicy;
import com.app.eventmanagmentsystem.model.enums.CheckInPolicy;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.pass.*;
import com.app.eventmanagmentsystem.repository.AttendeeRepository;
import com.app.eventmanagmentsystem.repository.EmailOutboxRepository;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import com.app.eventmanagmentsystem.repository.EventRepository;
import com.app.eventmanagmentsystem.service.impl.BulkPassService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BulkPassServiceTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventPassRepository eventPassRepository;

    @Autowired
    private AttendeeRepository attendeeRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private BulkPassService bulkPassService;

    private Event testEvent;
    private List<EventPass> testPasses;

    @BeforeEach
    void setUp() {
        testEvent = new Event();
        testEvent.setName("Tech Conference 2026");
        testEvent.setEventCode("TC26");
        testEvent.setPassSeriesCode("MAIN");
        testEvent.setStatus(EventStatus.ACTIVE);
        testEvent.setStartsAt(Instant.now().plusSeconds(86400));
        testEvent.setEndsAt(Instant.now().plusSeconds(172800));
        testEvent.setMaximumPasses(100);
        testEvent.setCheckInPolicy(CheckInPolicy.SINGLE_ENTRY);
        testEvent.setActivationPolicy(ActivationPolicy.SELF_SERVICE);
        testEvent = eventRepository.save(testEvent);

        testPasses = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            EventPass p = new EventPass();
            p.setEvent(testEvent);
            p.setPassNumber(String.format("TC26-MAIN-%04d", i));
            p.setSequenceNumber((long) i);
            p.setTokenHash("dummyhash" + i + UUID.randomUUID().toString().replace("-", ""));
            p.setStatus(PassStatus.NOT_ACTIVATED);
            p.setGeneratedAt(Instant.now());
            testPasses.add(eventPassRepository.save(p));
        }
    }

    private byte[] createTestExcel(List<String[]> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Attendees");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Full Name");
            header.createCell(1).setCellValue("Email");
            header.createCell(2).setCellValue("Mobile Number");
            header.createCell(3).setCellValue("Organization");
            header.createCell(4).setCellValue("Designation");

            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                String[] data = rows.get(r);
                for (int c = 0; c < data.length; c++) {
                    row.createCell(c).setCellValue(data[c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("Sample template generation returns non-empty Excel workbook")
    void testGenerateExcelTemplate() {
        byte[] bytes = bulkPassService.generateExcelTemplate();
        assertThat(bytes).isNotEmpty();
    }

    @Test
    @WithMockUser(authorities = {"PASS_VIEW"})
    @DisplayName("getPassStats calculates live pass counts")
    void testGetPassStats() {
        // Mark one as activated, one as revoked
        testPasses.get(0).setStatus(PassStatus.ACTIVATED);
        eventPassRepository.save(testPasses.get(0));

        testPasses.get(1).setStatus(PassStatus.REVOKED);
        eventPassRepository.save(testPasses.get(1));

        PassStatsResponse stats = bulkPassService.getPassStats(testEvent.getId());
        assertThat(stats.getTotalPasses()).isEqualTo(5);
        assertThat(stats.getActivatedPasses()).isEqualTo(1);
        assertThat(stats.getRevokedPasses()).isEqualTo(1);
        assertThat(stats.getAvailablePasses()).isEqualTo(3);
    }

    @Test
    @WithMockUser(authorities = {"PASS_VIEW"})
    @DisplayName("Scenario A: Successful preview with 5 eligible passes and 5 attendees")
    void testPreviewSuccessful() throws Exception {
        List<String[]> rows = List.of(
                new String[]{"John Doe", "john@example.com", "9876543210", "Tech Corp", "Dev"},
                new String[]{"Jane Smith", "jane@example.com", "9876543211", "Tech Corp", "Lead"},
                new String[]{"Bob Brown", "bob@example.com", "9876543212", "Dev Inc", "QA"},
                new String[]{"Alice White", "alice@example.com", "9876543213", "Cloud Co", "Architect"},
                new String[]{"Charlie Green", "charlie@example.com", "9876543214", "Data Lab", "Scientist"}
        );
        byte[] excelBytes = createTestExcel(rows);
        MockMultipartFile file = new MockMultipartFile("file", "attendees.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        BulkActivationPreviewResponse preview = bulkPassService.previewBulkActivation(
                testEvent.getId(), file, "RANGE", 1L, 5L, null);

        assertThat(preview.isCanConfirm()).isTrue();
        assertThat(preview.getEligibleCount()).isEqualTo(5);
        assertThat(preview.getInvalidRowCount()).isEqualTo(0);
        assertThat(preview.getConflictActivatedCount()).isEqualTo(0);
        assertThat(preview.getItems()).hasSize(5);
        assertThat(preview.getItems().get(0).getResultStatus()).isEqualTo("ELIGIBLE");
        assertThat(preview.getItems().get(0).getPassNumber()).isEqualTo("TC26-MAIN-0001");
    }

    @Test
    @WithMockUser(authorities = {"PASS_VIEW"})
    @DisplayName("Scenario B: Pass already activated in range is flagged as conflict")
    void testPreviewWithAlreadyActivatedConflict() throws Exception {
        // Activate pass 2
        Attendee existingAttendee = new Attendee();
        existingAttendee.setEvent(testEvent);
        existingAttendee.setFullName("Existing User");
        existingAttendee.setEmail("existing@example.com");
        existingAttendee.setRegisteredAt(Instant.now());
        existingAttendee.setRegistrationStatus(com.app.eventmanagmentsystem.model.enums.RegistrationStatus.CONFIRMED);
        existingAttendee = attendeeRepository.save(existingAttendee);

        EventPass pass2 = testPasses.get(1);
        pass2.setStatus(PassStatus.ACTIVATED);
        pass2.setAttendee(existingAttendee);
        eventPassRepository.save(pass2);

        List<String[]> rows = List.of(
                new String[]{"User One", "user1@example.com", "9876543210", "Org", "Role"},
                new String[]{"User Two", "user2@example.com", "9876543211", "Org", "Role"}
        );
        byte[] excelBytes = createTestExcel(rows);
        MockMultipartFile file = new MockMultipartFile("file", "attendees.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        BulkActivationPreviewResponse preview = bulkPassService.previewBulkActivation(
                testEvent.getId(), file, "RANGE", 1L, 2L, null);

        assertThat(preview.isCanConfirm()).isFalse();
        assertThat(preview.getConflictActivatedCount()).isEqualTo(1);
        assertThat(preview.getItems().get(1).getResultStatus()).isEqualTo("CONFLICT_ALREADY_ACTIVATED");
        assertThat(preview.getItems().get(1).getExistingAttendeeName()).isEqualTo("Existing User");
    }

    @Test
    @WithMockUser(authorities = {"PASS_VIEW"})
    @DisplayName("Scenario C: Revoked pass in range is flagged as conflict")
    void testPreviewWithRevokedPassConflict() throws Exception {
        EventPass pass2 = testPasses.get(1);
        pass2.setStatus(PassStatus.REVOKED);
        eventPassRepository.save(pass2);

        List<String[]> rows = List.of(
                new String[]{"User One", "user1@example.com", "9876543210", "Org", "Role"},
                new String[]{"User Two", "user2@example.com", "9876543211", "Org", "Role"}
        );
        byte[] excelBytes = createTestExcel(rows);
        MockMultipartFile file = new MockMultipartFile("file", "attendees.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        BulkActivationPreviewResponse preview = bulkPassService.previewBulkActivation(
                testEvent.getId(), file, "RANGE", 1L, 2L, null);

        assertThat(preview.isCanConfirm()).isFalse();
        assertThat(preview.getConflictRevokedCount()).isEqualTo(1);
        assertThat(preview.getItems().get(1).getResultStatus()).isEqualTo("CONFLICT_REVOKED");
    }

    @Test
    @WithMockUser(authorities = {"PASS_VIEW"})
    @DisplayName("Scenario D: Mismatch between uploaded attendees and selected series size")
    void testPreviewCountMismatch() throws Exception {
        // Upload 3 attendees for range of 5 passes
        List<String[]> rows = List.of(
                new String[]{"User One", "user1@example.com", "9876543210", "Org", "Role"},
                new String[]{"User Two", "user2@example.com", "9876543211", "Org", "Role"},
                new String[]{"User Three", "user3@example.com", "9876543212", "Org", "Role"}
        );
        byte[] excelBytes = createTestExcel(rows);
        MockMultipartFile file = new MockMultipartFile("file", "attendees.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        BulkActivationPreviewResponse preview = bulkPassService.previewBulkActivation(
                testEvent.getId(), file, "RANGE", 1L, 5L, null);

        assertThat(preview.isCanConfirm()).isFalse();
        assertThat(preview.getRejectionReason()).contains("Count mismatch");
    }

    @Test
    @WithMockUser(authorities = {"PASS_VIEW"})
    @DisplayName("Scenario E: Duplicate email in spreadsheet is flagged as invalid row")
    void testPreviewDuplicateEmailInSpreadsheet() throws Exception {
        List<String[]> rows = List.of(
                new String[]{"User One", "dup@example.com", "9876543210", "Org", "Role"},
                new String[]{"User Duplicate", "dup@example.com", "9876543211", "Org", "Role"}
        );
        byte[] excelBytes = createTestExcel(rows);
        MockMultipartFile file = new MockMultipartFile("file", "attendees.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        BulkActivationPreviewResponse preview = bulkPassService.previewBulkActivation(
                testEvent.getId(), file, "RANGE", 1L, 2L, null);

        assertThat(preview.isCanConfirm()).isFalse();
        assertThat(preview.getInvalidRowCount()).isEqualTo(1);
        assertThat(preview.getItems().get(1).getResultStatus()).isEqualTo("INVALID_DATA");
        assertThat(preview.getItems().get(1).getConflictReason()).contains("Duplicate email");
    }

    @Test
    @WithMockUser(authorities = {"PASS_ACTIVATE"})
    @DisplayName("Scenario F: Successful transactional bulk activation enqueues emails and persists attendees")
    void testConfirmBulkActivationSuccess() {
        BulkActivationAssignmentDto a1 = BulkActivationAssignmentDto.builder()
                .passId(testPasses.get(0).getId())
                .attendeeFullName("Alice Wonder")
                .attendeeEmail("alice@example.com")
                .attendeeMobileNumber("9876543210")
                .attendeeOrganization("Wonderland")
                .build();

        BulkActivationAssignmentDto a2 = BulkActivationAssignmentDto.builder()
                .passId(testPasses.get(1).getId())
                .attendeeFullName("Bob Hope")
                .attendeeEmail("bob@example.com")
                .attendeeMobileNumber("9876543211")
                .attendeeOrganization("Hope Ltd")
                .build();

        BulkActivationConfirmRequest request = BulkActivationConfirmRequest.builder()
                .eventId(testEvent.getId())
                .batchId("BATCH-TEST-001")
                .assignments(List.of(a1, a2))
                .build();

        BulkActivationConfirmResponse response = bulkPassService.confirmBulkActivation(request);

        assertThat(response.getActivatedCount()).isEqualTo(2);
        assertThat(response.getEmailsQueued()).isEqualTo(2);

        EventPass p1 = eventPassRepository.findById(testPasses.get(0).getId()).orElseThrow();
        assertThat(p1.getStatus()).isEqualTo(PassStatus.ACTIVATED);
        assertThat(p1.getAttendee()).isNotNull();
        assertThat(p1.getAttendee().getFullName()).isEqualTo("Alice Wonder");

        EventPass p2 = eventPassRepository.findById(testPasses.get(1).getId()).orElseThrow();
        assertThat(p2.getStatus()).isEqualTo(PassStatus.ACTIVATED);
        assertThat(p2.getAttendee().getFullName()).isEqualTo("Bob Hope");
    }

    @Test
    @WithMockUser(authorities = {"PASS_ACTIVATE"})
    @DisplayName("Scenario G: Confirm rolls back completely if any pass in batch is already activated")
    void testConfirmBulkActivationAllOrNothingRollback() {
        // Pre-activate pass 1
        testPasses.get(0).setStatus(PassStatus.ACTIVATED);
        eventPassRepository.save(testPasses.get(0));

        BulkActivationAssignmentDto a1 = BulkActivationAssignmentDto.builder()
                .passId(testPasses.get(0).getId())
                .attendeeFullName("Alice Wonder")
                .attendeeEmail("alice@example.com")
                .build();

        BulkActivationAssignmentDto a2 = BulkActivationAssignmentDto.builder()
                .passId(testPasses.get(1).getId())
                .attendeeFullName("Bob Hope")
                .attendeeEmail("bob@example.com")
                .build();

        BulkActivationConfirmRequest request = BulkActivationConfirmRequest.builder()
                .eventId(testEvent.getId())
                .assignments(List.of(a1, a2))
                .build();

        assertThatThrownBy(() -> bulkPassService.confirmBulkActivation(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("already activated");

        // Pass 2 must remain NOT_ACTIVATED due to transactional rollback
        EventPass p2 = eventPassRepository.findById(testPasses.get(1).getId()).orElseThrow();
        assertThat(p2.getStatus()).isEqualTo(PassStatus.NOT_ACTIVATED);
        assertThat(p2.getAttendee()).isNull();
    }
}
