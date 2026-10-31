package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.DuplicateResourceException;
import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.Attendee;
import com.app.eventmanagmentsystem.model.EmailOutbox;
import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.EventDay;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.enums.*;
import com.app.eventmanagmentsystem.payload.event.EventDayResponse;
import com.app.eventmanagmentsystem.payload.pass.*;
import com.app.eventmanagmentsystem.repository.*;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkPassService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final EventRepository eventRepository;
    private final EventPassRepository passRepository;
    private final AttendeeRepository attendeeRepository;
    private final CheckInRepository checkInRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final AuditLogService auditLogService;

    @PreAuthorize("hasAuthority('PASS_VIEW')")
    @Transactional(readOnly = true)
    public PassStatsResponse getPassStats(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));

        long total = passRepository.countByEventId(eventId);
        long activated = passRepository.countByEventIdAndStatus(eventId, PassStatus.ACTIVATED);
        long available = passRepository.countByEventIdAndStatus(eventId, PassStatus.NOT_ACTIVATED);
        long revoked = passRepository.countByEventIdAndStatus(eventId, PassStatus.REVOKED);
        long checkedIn = checkInRepository.countByEventId(eventId);

        return PassStatsResponse.builder()
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .eventName(event.getName())
                .totalPasses(total)
                .activatedPasses(activated)
                .availablePasses(available)
                .revokedPasses(revoked)
                .checkedInCount(checkedIn)
                .build();
    }

    @PreAuthorize("hasAuthority('PASS_VIEW')")
    @Transactional(readOnly = true)
    public List<PassExportItemResponse> exportPasses(
            UUID eventId,
            PassStatus status,
            Long fromSequence,
            Long toSequence,
            UUID dayId) {
        return exportPasses(eventId, status, null, fromSequence, toSequence, dayId);
    }

    @PreAuthorize("hasAuthority('PASS_VIEW')")
    @Transactional(readOnly = true)
    public List<PassExportItemResponse> exportPasses(
            UUID eventId,
            PassStatus status,
            PassCategory category,
            Long fromSequence,
            Long toSequence,
            UUID dayId) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));

        List<EventPass> passes;
        if (fromSequence != null && toSequence != null) {
            passes = passRepository.findByEventIdAndSequenceNumberBetweenOrderBySequenceNumberAsc(eventId, fromSequence, toSequence);
        } else if (status != null) {
            passes = passRepository.findByEventIdAndStatusOrderBySequenceNumberAsc(eventId, status);
        } else {
            passes = passRepository.findByEventIdOrderBySequenceNumberAsc(eventId);
        }

        // Apply filters if needed
        return passes.stream()
                .filter(p -> status == null || p.getStatus() == status)
                .filter(p -> category == null || p.getCategory() == category)
                .filter(p -> dayId == null || p.getValidDays().isEmpty() || p.getValidDays().stream().anyMatch(d -> d.getId().equals(dayId)))
                .map(p -> toExportItem(p, event))
                .collect(Collectors.toList());
    }

    private PassExportItemResponse toExportItem(EventPass pass, Event event) {
        String attendeeName = pass.getAttendee() != null ? pass.getAttendee().getFullName() : null;
        String attendeeEmail = pass.getAttendee() != null ? pass.getAttendee().getEmail() : null;
        String attendeeMobile = pass.getAttendee() != null ? pass.getAttendee().getMobileNumber() : null;

        List<EventDayResponse> validDays = pass.getValidDays() == null ? Collections.emptyList() :
                pass.getValidDays().stream()
                        .sorted(Comparator.comparingInt(EventDay::getDayNumber))
                        .map(d -> EventDayResponse.builder()
                                .id(d.getId())
                                .dayNumber(d.getDayNumber())
                                .eventDate(d.getEventDate())
                                .label(d.getLabel())
                                .build())
                        .collect(Collectors.toList());

        // Standard QR data payload pointing to pass activation / inspection
        String qrData = pass.getPassNumber();

        return PassExportItemResponse.builder()
                .passId(pass.getId())
                .passNumber(pass.getPassNumber())
                .sequenceNumber(pass.getSequenceNumber())
                .category(pass.getCategory())
                .status(pass.getStatus())
                .eventId(event.getId())
                .eventName(event.getName())
                .eventCode(event.getEventCode())
                .venue(event.getVenue())
                .startsAt(event.getStartsAt())
                .endsAt(event.getEndsAt())
                .validDays(validDays)
                .attendeeName(attendeeName)
                .attendeeEmail(attendeeEmail)
                .attendeeMobileNumber(attendeeMobile)
                .qrData(qrData)
                .build();
    }

    public byte[] generateExcelTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Attendees");

            // Header Style
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.LEFT);

            // Row 0: Headers
            Row headerRow = sheet.createRow(0);
            String[] columns = {"Full Name", "Email", "Mobile Number", "Organization", "Designation"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            // Sample Data Rows
            String[][] sampleData = {
                    {"Rahul Sharma", "rahul.sharma@example.com", "9876543210", "Acme Tech Pvt Ltd", "Senior Engineer"},
                    {"Priya Verma", "priya.verma@example.com", "9876543211", "Global Cloud Corp", "Product Manager"},
                    {"Amit Singh", "amit.singh@example.com", "9876543212", "Innovate Design", "UI/UX Designer"}
            };

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setAlignment(HorizontalAlignment.LEFT);

            for (int r = 0; r < sampleData.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < sampleData[r].length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(sampleData[r][c]);
                    cell.setCellStyle(dataStyle);
                }
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Excel template: {}", e.getMessage(), e);
            throw new InvalidRequestException("Failed to generate Excel template: " + e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('PASS_VIEW')")
    @Transactional(readOnly = true)
    public BulkActivationPreviewResponse previewBulkActivation(
            UUID eventId,
            MultipartFile file,
            String selectionMode,
            Long fromSequence,
            Long toSequence,
            List<UUID> selectedPassIds) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new InvalidRequestException("Cannot activate passes for a cancelled event.");
        }

        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Please upload an Excel spreadsheet (.xlsx or .xls).");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".xlsx") && !filename.toLowerCase().endsWith(".xls"))) {
            throw new InvalidRequestException("Unsupported file type. Please upload an Excel file (.xlsx or .xls).");
        }

        // 1. Parse Excel file
        List<ParsedRow> parsedRows = parseExcelRows(file);
        if (parsedRows.isEmpty()) {
            throw new InvalidRequestException("Uploaded spreadsheet is empty. Please provide attendee data rows.");
        }
        if (parsedRows.size() > 500) {
            throw new InvalidRequestException("Maximum batch size is 500 attendees per bulk activation.");
        }

        // 2. Fetch target passes according to selectionMode
        String effectiveMode = selectionMode != null ? selectionMode.toUpperCase() : "AUTO";
        List<EventPass> targetPasses;

        if ("RANGE".equals(effectiveMode)) {
            if (fromSequence == null || toSequence == null) {
                throw new InvalidRequestException("fromSequence and toSequence are required for RANGE selection.");
            }
            if (fromSequence > toSequence) {
                throw new InvalidRequestException("fromSequence cannot be greater than toSequence.");
            }
            targetPasses = passRepository.findByEventIdAndSequenceNumberBetweenOrderBySequenceNumberAsc(eventId, fromSequence, toSequence);
        } else if ("INDIVIDUAL".equals(effectiveMode)) {
            if (selectedPassIds == null || selectedPassIds.isEmpty()) {
                throw new InvalidRequestException("selectedPassIds cannot be empty for INDIVIDUAL selection.");
            }
            targetPasses = passRepository.findByEventIdAndIdIn(eventId, selectedPassIds);
            targetPasses.sort(Comparator.comparing(p -> p.getSequenceNumber() != null ? p.getSequenceNumber() : 0L));
        } else { // AUTO mode: grab earliest unactivated passes or sequential passes
            targetPasses = passRepository.findByEventIdAndStatusOrderBySequenceNumberAsc(eventId, PassStatus.NOT_ACTIVATED);
            if (targetPasses.size() > parsedRows.size()) {
                targetPasses = targetPasses.subList(0, parsedRows.size());
            }
        }

        // 3. Match rows to passes and analyze conflicts
        List<AssignmentPreviewItem> previewItems = new ArrayList<>();
        Set<String> seenEmailsInSpreadsheet = new HashSet<>();
        int eligibleCount = 0;
        int conflictActivatedCount = 0;
        int conflictRevokedCount = 0;
        int conflictAttendeeExistsCount = 0;
        int invalidRowCount = 0;

        int maxRows = Math.max(parsedRows.size(), targetPasses.size());

        for (int i = 0; i < maxRows; i++) {
            ParsedRow attendeeRow = i < parsedRows.size() ? parsedRows.get(i) : null;
            EventPass pass = i < targetPasses.size() ? targetPasses.get(i) : null;

            int rowNumber = attendeeRow != null ? attendeeRow.rowNumber : (i + 1);
            String resultStatus = "ELIGIBLE";
            String conflictReason = null;

            // Check if attendee row exists
            if (attendeeRow == null) {
                resultStatus = "UNMATCHED_PASS";
                conflictReason = "Pass in selected series has no corresponding attendee in upload.";
            } else if (pass == null) {
                resultStatus = "INVALID_DATA";
                conflictReason = "No pass available in the selected range for this attendee.";
                invalidRowCount++;
            } else {
                // Check row validity
                if (attendeeRow.fullName == null || attendeeRow.fullName.trim().isEmpty()) {
                    resultStatus = "INVALID_DATA";
                    conflictReason = "Attendee Full Name is required.";
                    invalidRowCount++;
                } else if (attendeeRow.email == null || !EMAIL_PATTERN.matcher(attendeeRow.email).matches()) {
                    resultStatus = "INVALID_DATA";
                    conflictReason = "Valid email address is required.";
                    invalidRowCount++;
                } else if (seenEmailsInSpreadsheet.contains(attendeeRow.email.toLowerCase())) {
                    resultStatus = "INVALID_DATA";
                    conflictReason = "Duplicate email within uploaded spreadsheet.";
                    invalidRowCount++;
                } else {
                    seenEmailsInSpreadsheet.add(attendeeRow.email.toLowerCase());

                    // Check Pass Status
                    if (pass.getStatus() == PassStatus.REVOKED) {
                        resultStatus = "CONFLICT_REVOKED";
                        conflictReason = "Pass is revoked and cannot be activated.";
                        conflictRevokedCount++;
                    } else if (pass.getStatus() == PassStatus.ACTIVATED) {
                        resultStatus = "CONFLICT_ALREADY_ACTIVATED";
                        String existingHolder = pass.getAttendee() != null ? pass.getAttendee().getFullName() : "another attendee";
                        conflictReason = "Pass is already activated for " + existingHolder + ".";
                        conflictActivatedCount++;
                    } else if (attendeeRepository.existsByEventAndEmail(eventId, attendeeRow.email.toLowerCase().trim())) {
                        resultStatus = "CONFLICT_ATTENDEE_EXISTS";
                        conflictReason = "Attendee with email " + attendeeRow.email + " is already registered for this event.";
                        conflictAttendeeExistsCount++;
                    } else {
                        resultStatus = "ELIGIBLE";
                        eligibleCount++;
                    }
                }
            }

            AssignmentPreviewItem item = AssignmentPreviewItem.builder()
                    .rowNumber(rowNumber)
                    .passId(pass != null ? pass.getId() : null)
                    .passNumber(pass != null ? pass.getPassNumber() : null)
                    .sequenceNumber(pass != null ? pass.getSequenceNumber() : null)
                    .currentPassStatus(pass != null ? pass.getStatus() : null)
                    .existingAttendeeName(pass != null && pass.getAttendee() != null ? pass.getAttendee().getFullName() : null)
                    .attendeeFullName(attendeeRow != null ? attendeeRow.fullName : null)
                    .attendeeEmail(attendeeRow != null ? attendeeRow.email : null)
                    .attendeeMobileNumber(attendeeRow != null ? attendeeRow.mobileNumber : null)
                    .attendeeOrganization(attendeeRow != null ? attendeeRow.organization : null)
                    .attendeeDesignation(attendeeRow != null ? attendeeRow.designation : null)
                    .resultStatus(resultStatus)
                    .conflictReason(conflictReason)
                    .build();

            previewItems.add(item);
        }

        boolean countMismatch = parsedRows.size() != targetPasses.size();
        boolean canConfirm = !countMismatch && invalidRowCount == 0 && conflictActivatedCount == 0
                && conflictRevokedCount == 0 && conflictAttendeeExistsCount == 0 && eligibleCount == parsedRows.size();

        String rejectionReason = null;
        if (!canConfirm) {
            if (countMismatch) {
                rejectionReason = String.format("Count mismatch: %d attendees uploaded but %d passes selected in series.",
                        parsedRows.size(), targetPasses.size());
            } else if (conflictActivatedCount > 0) {
                rejectionReason = conflictActivatedCount + " pass(es) in the selected series are already activated.";
            } else if (conflictRevokedCount > 0) {
                rejectionReason = conflictRevokedCount + " pass(es) in the selected series are revoked.";
            } else if (conflictAttendeeExistsCount > 0) {
                rejectionReason = conflictAttendeeExistsCount + " attendee(s) are already registered for this event.";
            } else if (invalidRowCount > 0) {
                rejectionReason = invalidRowCount + " spreadsheet row(s) contain validation errors.";
            }
        }

        String batchId = "BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        return BulkActivationPreviewResponse.builder()
                .batchId(batchId)
                .eventId(event.getId())
                .eventName(event.getName())
                .eventCode(event.getEventCode())
                .selectionMode(effectiveMode)
                .fromSequence(fromSequence)
                .toSequence(toSequence)
                .totalUploadedRows(parsedRows.size())
                .totalTargetPasses(targetPasses.size())
                .eligibleCount(eligibleCount)
                .conflictActivatedCount(conflictActivatedCount)
                .conflictRevokedCount(conflictRevokedCount)
                .conflictAttendeeExistsCount(conflictAttendeeExistsCount)
                .invalidRowCount(invalidRowCount)
                .canConfirm(canConfirm)
                .rejectionReason(rejectionReason)
                .items(previewItems)
                .generatedAt(Instant.now())
                .build();
    }

    @PreAuthorize("hasAuthority('PASS_ACTIVATE')")
    @Transactional
    public BulkActivationConfirmResponse confirmBulkActivation(BulkActivationConfirmRequest request) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event", request.getEventId().toString()));

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new InvalidRequestException("Cannot activate passes for a cancelled event.");
        }

        Instant now = Instant.now();
        if (event.getActivationStartsAt() != null && now.isBefore(event.getActivationStartsAt())) {
            throw new InvalidRequestException("Pass activation window has not opened yet.");
        }
        if (event.getActivationEndsAt() != null && now.isAfter(event.getActivationEndsAt())) {
            throw new InvalidRequestException("Pass activation window has closed.");
        }

        List<BulkActivationAssignmentDto> assignments = request.getAssignments();
        if (assignments == null || assignments.isEmpty()) {
            throw new InvalidRequestException("Assignments list cannot be empty.");
        }

        // Check for duplicate emails within the request batch
        Set<String> emailsInBatch = new HashSet<>();
        for (BulkActivationAssignmentDto a : assignments) {
            String norm = a.getAttendeeEmail().toLowerCase().trim();
            if (!emailsInBatch.add(norm)) {
                throw new DuplicateResourceException("Duplicate email in activation batch: " + a.getAttendeeEmail());
            }
        }

        // Collect pass IDs to lock
        List<UUID> passIds = assignments.stream().map(BulkActivationAssignmentDto::getPassId).collect(Collectors.toList());

        // Pessimistic Write Lock on all target passes to prevent concurrent state changes
        List<EventPass> lockedPasses = passRepository.findAllByIdForUpdate(passIds);
        Map<UUID, EventPass> passMap = lockedPasses.stream().collect(Collectors.toMap(EventPass::getId, p -> p));

        if (lockedPasses.size() != passIds.size()) {
            throw new InvalidRequestException("One or more requested passes could not be found.");
        }

        // Strict All-or-Nothing re-validation under lock
        for (BulkActivationAssignmentDto a : assignments) {
            EventPass pass = passMap.get(a.getPassId());
            if (pass == null) {
                throw new InvalidRequestException("Pass " + a.getPassId() + " not found.");
            }
            if (!pass.getEvent().getId().equals(event.getId())) {
                throw new InvalidRequestException("Pass " + pass.getPassNumber() + " belongs to a different event.");
            }
            if (pass.getStatus() == PassStatus.REVOKED) {
                throw new InvalidRequestException("Cannot activate revoked pass " + pass.getPassNumber() + ".");
            }
            if (pass.getStatus() == PassStatus.ACTIVATED) {
                throw new InvalidRequestException("Pass " + pass.getPassNumber() + " is already activated.");
            }

            String normEmail = a.getAttendeeEmail().toLowerCase().trim();
            if (attendeeRepository.existsByEventAndEmail(event.getId(), normEmail)) {
                throw new DuplicateResourceException("An attendee with email " + a.getAttendeeEmail() + " is already registered.");
            }
        }

        // Apply all activations transactionally
        List<String> activatedPassNumbers = new ArrayList<>(assignments.size());
        int emailsQueued = 0;

        for (BulkActivationAssignmentDto a : assignments) {
            EventPass pass = passMap.get(a.getPassId());

            Attendee attendee = new Attendee();
            attendee.setEvent(event);
            attendee.setFullName(a.getAttendeeFullName().trim());
            attendee.setEmail(a.getAttendeeEmail().toLowerCase().trim());
            attendee.setMobileNumber(a.getAttendeeMobileNumber());
            attendee.setOrganization(a.getAttendeeOrganization());
            attendee.setDesignation(a.getAttendeeDesignation());
            attendee.setRegistrationStatus(RegistrationStatus.CONFIRMED);
            attendee.setRegisteredAt(now);
            Attendee savedAttendee = attendeeRepository.save(attendee);

            pass.setAttendee(savedAttendee);
            pass.setStatus(PassStatus.ACTIVATED);
            pass.setActivatedAt(now);
            passRepository.save(pass);

            // Enqueue transactional outbox email
            enqueueActivationEmail(pass, savedAttendee);
            emailsQueued++;

            activatedPassNumbers.add(pass.getPassNumber());
        }

        String batchReference = request.getBatchId() != null ? request.getBatchId() :
                "BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        auditLogService.log("PASS_BULK_ACTIVATE", "EventPass", batchReference,
                event.getId().toString(), AuditOutcome.SUCCESS, null,
                "Bulk activated " + assignments.size() + " passes for event " + event.getEventCode());

        return BulkActivationConfirmResponse.builder()
                .batchReference(batchReference)
                .eventId(event.getId())
                .eventName(event.getName())
                .activatedCount(assignments.size())
                .emailsQueued(emailsQueued)
                .completedAt(now)
                .activatedPassNumbers(activatedPassNumbers)
                .build();
    }

    private void enqueueActivationEmail(EventPass pass, Attendee attendee) {
        try {
            EmailOutbox outbox = new EmailOutbox();
            outbox.setRecipientEmail(attendee.getEmail());
            outbox.setRecipientName(attendee.getFullName());
            outbox.setNotificationType(NotificationType.PASS_ACTIVATION);
            outbox.setReferenceId(pass.getId().toString());
            outbox.setStatus(EmailOutboxStatus.PENDING);
            outbox.setMaxAttempts(5);
            outbox.setPayload("{\"passNumber\":\"" + pass.getPassNumber() + "\",\"eventId\":\""
                    + pass.getEvent().getId() + "\"}");
            emailOutboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to enqueue activation email for pass {}: {}", pass.getId(), e.getMessage());
        }
    }

    private List<ParsedRow> parseExcelRows(MultipartFile file) {
        List<ParsedRow> rows = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return rows;
            }

            int headerRowIndex = 0;
            Row headerRow = sheet.getRow(headerRowIndex);
            if (headerRow == null) {
                return rows;
            }

            // Map header column names to indexes
            Map<String, Integer> colMap = new HashMap<>();
            for (Cell cell : headerRow) {
                String val = getCellStringValue(cell).toLowerCase().replaceAll("[^a-z]", "");
                if (val.contains("name")) colMap.put("name", cell.getColumnIndex());
                else if (val.contains("email") || val.contains("mail")) colMap.put("email", cell.getColumnIndex());
                else if (val.contains("mobile") || val.contains("phone") || val.contains("contact")) colMap.put("mobile", cell.getColumnIndex());
                else if (val.contains("org") || val.contains("company")) colMap.put("org", cell.getColumnIndex());
                else if (val.contains("desig") || val.contains("role") || val.contains("title")) colMap.put("desig", cell.getColumnIndex());
            }

            int nameCol = colMap.getOrDefault("name", 0);
            int emailCol = colMap.getOrDefault("email", 1);
            int mobileCol = colMap.getOrDefault("mobile", 2);
            int orgCol = colMap.getOrDefault("org", 3);
            int desigCol = colMap.getOrDefault("desig", 4);

            int lastRow = sheet.getLastRowNum();
            for (int r = headerRowIndex + 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String name = getCellStringValue(row.getCell(nameCol));
                String email = getCellStringValue(row.getCell(emailCol));
                String mobile = getCellStringValue(row.getCell(mobileCol));
                String org = getCellStringValue(row.getCell(orgCol));
                String desig = getCellStringValue(row.getCell(desigCol));

                // Skip completely blank rows
                if (name.isEmpty() && email.isEmpty() && mobile.isEmpty()) {
                    continue;
                }

                rows.add(new ParsedRow(r + 1, name, email, mobile, org, desig));
            }
        } catch (Exception e) {
            log.error("Failed to read Excel workbook: {}", e.getMessage(), e);
            throw new InvalidRequestException("Failed to read Excel file. Please ensure it is a valid .xlsx or .xls file.");
        }
        return rows;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        switch (type) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                // If it's a mobile number or integer
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num)) {
                    return String.format("%.0f", num);
                }
                return String.valueOf(num);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    private static class ParsedRow {
        final int rowNumber;
        final String fullName;
        final String email;
        final String mobileNumber;
        final String organization;
        final String designation;

        ParsedRow(int rowNumber, String fullName, String email, String mobileNumber, String organization, String designation) {
            this.rowNumber = rowNumber;
            this.fullName = fullName;
            this.email = email;
            this.mobileNumber = mobileNumber;
            this.organization = organization;
            this.designation = designation;
        }
    }
}

