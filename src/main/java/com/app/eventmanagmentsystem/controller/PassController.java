package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.pass.*;
import com.app.eventmanagmentsystem.security.AppUserDetails;
import com.app.eventmanagmentsystem.service.impl.BulkPassService;
import com.app.eventmanagmentsystem.service.impl.PassActivationService;
import com.app.eventmanagmentsystem.service.impl.PassGenerationService;
import com.app.eventmanagmentsystem.service.impl.PassValidityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Tag(name = "Passes", description = "Endpoints for batch pass generation, bulk activation, validity management, and inspection")
@RestController
@RequestMapping("/api/v1/passes")
@RequiredArgsConstructor
public class PassController {

        private final PassGenerationService passGenerationService;
        private final PassActivationService passActivationService;
        private final BulkPassService bulkPassService;
        private final PassValidityService passValidityService;

        private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
                        "sequenceNumber", "passNumber", "status", "generatedAt", "activatedAt");

        @Operation(summary = "Generate passes in batch", description = "Generates passes sequentially with format {EVENT_CODE}-{SERIES_CODE}-{SEQUENCE}. "
                        +
                        "Returns raw QR tokens ONLY on this call. Requires PASS_GENERATE permission.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Passes generated successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Batch exceeds event limit or event is cancelled"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Event not found")
        })
        @PostMapping("/generate")
        public ResponseEntity<ApiResponse<List<PassResponse>>> generatePasses(
                        @Valid @RequestBody GeneratePassRequest request) {
                List<PassResponse> passes = passGenerationService.generatePasses(request);
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success("Passes generated", passes));
        }

        @Operation(summary = "Validate pass by token", description = "Public endpoint to inspect a pass's validity and event metadata from a scanned raw token before submitting activation.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pass validation status retrieved"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid token")
        })
        @SecurityRequirements
        @GetMapping("/validate")
        public ResponseEntity<ApiResponse<ValidatePassResponse>> validatePass(
                        @Parameter(description = "Raw pass token from QR code") @RequestParam String token) {
                ValidatePassResponse response = passActivationService.validatePass(token);
                return ResponseEntity.ok(ApiResponse.success(response));
        }

        @Operation(summary = "Activate a pass", description = "Atomically registers attendee info, activates the pass, and enqueues an activation email via transactional outbox. Single-use only.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pass activated successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid QR token or wrong event"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Pass already activated or duplicate attendee email"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "Pass is revoked")
        })
        @SecurityRequirements
        @PostMapping("/activate")
        public ResponseEntity<ApiResponse<PassResponse>> activatePass(
                        @Valid @RequestBody ActivatePassRequest request) {
                PassResponse response = passActivationService.activatePass(request);
                return ResponseEntity.ok(ApiResponse.success("Pass activated successfully", response));
        }

        @Operation(summary = "List and search passes by event", description = "Retrieves paginated passes for an event with server-side search, filtering, and sorting. Requires PASS_VIEW permission.")
        @GetMapping("/event/{eventId}")
        public ResponseEntity<ApiResponse<Page<PassResponse>>> listPasses(
                        @Parameter(description = "Event UUID") @PathVariable UUID eventId,
                        @Parameter(description = "Search query by pass #, sequence, attendee name or email") @RequestParam(required = false) String search,
                        @Parameter(description = "Pass status filter") @RequestParam(required = false) PassStatus status,
                        @Parameter(description = "Check-in status: ALL, CHECKED_IN, NOT_CHECKED_IN") @RequestParam(required = false) String checkInStatus,
                        @Parameter(description = "Permitted event day filter") @RequestParam(required = false) UUID eventDayId,
                        @Parameter(description = "From sequence number") @RequestParam(required = false) Long fromSequence,
                        @Parameter(description = "To sequence number") @RequestParam(required = false) Long toSequence,
                        @Parameter(description = "Field to sort by") @RequestParam(required = false, defaultValue = "sequenceNumber") String sortBy,
                        @Parameter(description = "Sort direction: asc or desc") @RequestParam(required = false, defaultValue = "asc") String sortDir,
                        @Parameter(description = "Page number (0-based)") @RequestParam(required = false, defaultValue = "0") int page,
                        @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "20") int size) {

                String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "sequenceNumber";
                Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
                Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                                Sort.by(direction, safeSortBy));

                Page<PassResponse> result = passGenerationService.searchPassesByEvent(
                                eventId, search, status, checkInStatus, eventDayId, fromSequence, toSequence, pageable);
                return ResponseEntity.ok(ApiResponse.success(result));
        }

        @Operation(summary = "Update pass validity in bulk", description = "Updates permitted event days for multiple passes, a sequence range, or all eligible passes. Requires PASS_GENERATE or EVENT_UPDATE permission.")
        @PostMapping("/event/{eventId}/validity")
        public ResponseEntity<ApiResponse<UpdatePassValidityResponse>> updatePassValidityBulk(
                        @Parameter(description = "Event UUID") @PathVariable UUID eventId,
                        @Valid @RequestBody UpdatePassValidityRequest request) {
                UpdatePassValidityResponse response = passValidityService.updatePassValidity(eventId, request);
                return ResponseEntity.ok(ApiResponse.success("Pass validity updated successfully", response));
        }

        @Operation(summary = "Update validity for a single pass", description = "Updates permitted event days for an individual pass. Requires PASS_GENERATE or EVENT_UPDATE permission.")
        @PutMapping("/{passId}/validity")
        public ResponseEntity<ApiResponse<PassResponse>> updateSinglePassValidity(
                        @Parameter(description = "Pass UUID") @PathVariable UUID passId,
                        @RequestBody(required = false) List<UUID> validDayIds) {
                PassResponse response = passValidityService.updateSinglePassValidity(passId, validDayIds);
                return ResponseEntity.ok(ApiResponse.success("Pass validity updated successfully", response));
        }

        @Operation(summary = "Get live pass statistics for an event", description = "Returns total, activated, available, revoked, and checked-in counts. Requires PASS_VIEW permission.")
        @GetMapping("/event/{eventId}/stats")
        public ResponseEntity<ApiResponse<PassStatsResponse>> getPassStats(
                        @Parameter(description = "Event UUID") @PathVariable UUID eventId) {
                return ResponseEntity.ok(ApiResponse.success(bulkPassService.getPassStats(eventId)));
        }

        @Operation(summary = "Export passes for bulk QR printing", description = "Exports passes matching filter criteria with full QR encoding payloads. Requires PASS_VIEW permission.")
        @GetMapping("/event/{eventId}/export")
        public ResponseEntity<ApiResponse<List<PassExportItemResponse>>> exportPasses(
                        @Parameter(description = "Event UUID") @PathVariable UUID eventId,
                        @Parameter(description = "Pass status filter") @RequestParam(required = false) PassStatus status,
                        @Parameter(description = "Starting sequence number") @RequestParam(required = false) Long fromSequence,
                        @Parameter(description = "Ending sequence number") @RequestParam(required = false) Long toSequence,
                        @Parameter(description = "Event day filter") @RequestParam(required = false) UUID dayId) {
                List<PassExportItemResponse> items = bulkPassService.exportPasses(eventId, status, fromSequence,
                                toSequence, dayId);
                return ResponseEntity.ok(ApiResponse.success("Passes retrieved for export", items));
        }

        @Operation(summary = "Download sample bulk activation Excel template", description = "Downloads a formatted .xlsx sample template with attendee columns.")
        @GetMapping(value = "/bulk-activation/template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        public ResponseEntity<byte[]> downloadSampleTemplate() {
                byte[] excelBytes = bulkPassService.generateExcelTemplate();
                return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"attendee_bulk_activation_template.xlsx\"")
                                .contentType(MediaType.parseMediaType(
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                .body(excelBytes);
        }

        @Operation(summary = "Preview bulk activation assignments and conflicts", description = "Validates uploaded Excel attendee rows against selected pass series. Requires PASS_VIEW permission.")
        @PostMapping(value = "/event/{eventId}/bulk-activation/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ApiResponse<BulkActivationPreviewResponse>> previewBulkActivation(
                        @Parameter(description = "Event UUID") @PathVariable UUID eventId,
                        @RequestParam("file") MultipartFile file,
                        @RequestParam(value = "selectionMode", defaultValue = "AUTO") String selectionMode,
                        @RequestParam(value = "fromSequence", required = false) Long fromSequence,
                        @RequestParam(value = "toSequence", required = false) Long toSequence,
                        @RequestParam(value = "selectedPassIds", required = false) List<UUID> selectedPassIds) {

                BulkActivationPreviewResponse preview = bulkPassService.previewBulkActivation(
                                eventId, file, selectionMode, fromSequence, toSequence, selectedPassIds);
                return ResponseEntity.ok(ApiResponse.success(preview));
        }

        @Operation(summary = "Confirm bulk activation batch", description = "Transactionally locks passes, registers attendees, enqueues emails, and activates passes. Requires PASS_ACTIVATE permission.")
        @PostMapping("/event/{eventId}/bulk-activation/confirm")
        public ResponseEntity<ApiResponse<BulkActivationConfirmResponse>> confirmBulkActivation(
                        @Parameter(description = "Event UUID") @PathVariable UUID eventId,
                        @Valid @RequestBody BulkActivationConfirmRequest request) {

                request.setEventId(eventId);
                BulkActivationConfirmResponse response = bulkPassService.confirmBulkActivation(request);
                return ResponseEntity.ok(ApiResponse.success("Bulk activation confirmed successfully", response));
        }

        @Operation(summary = "Get pass details", description = "Retrieves pass details by pass UUID. Requires PASS_VIEW permission.")
        @GetMapping("/{passId}")
        public ResponseEntity<ApiResponse<PassResponse>> getPass(
                        @Parameter(description = "Pass UUID") @PathVariable UUID passId) {
                return ResponseEntity.ok(ApiResponse.success(passGenerationService.getPass(passId)));
        }

        @Operation(summary = "Revoke pass", description = "Revokes a pass by UUID. Requires PASS_REVOKE permission.")
        @PostMapping("/{passId}/revoke")
        public ResponseEntity<ApiResponse<Void>> revokePass(
                        @Parameter(description = "Pass UUID") @PathVariable UUID passId,
                        @RequestParam(required = false, defaultValue = "") String reason,
                        @AuthenticationPrincipal AppUserDetails userDetails) {

                String revokedById = userDetails != null && userDetails.getUserId() != null
                                ? userDetails.getUserId().toString()
                                : "SYSTEM";
                String revokedByName = userDetails != null && userDetails.getFullName() != null
                                ? userDetails.getFullName()
                                : "Administrator";

                passActivationService.revokePass(passId, reason, revokedById, revokedByName);
                return ResponseEntity.ok(ApiResponse.success("Pass revoked successfully", null));
        }
}
