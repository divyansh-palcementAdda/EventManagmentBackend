package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.pass.ActivatePassRequest;
import com.app.eventmanagmentsystem.payload.pass.GeneratePassRequest;
import com.app.eventmanagmentsystem.payload.pass.PassResponse;
import com.app.eventmanagmentsystem.service.impl.PassActivationService;
import com.app.eventmanagmentsystem.service.impl.PassGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Passes", description = "Endpoints for batch pass generation, transactional activation, and inspection")
@RestController
@RequestMapping("/api/v1/passes")
@RequiredArgsConstructor
public class PassController {

    private final PassGenerationService passGenerationService;
    private final PassActivationService passActivationService;

    @Operation(
            summary = "Generate passes in batch",
            description = "Generates passes sequentially with format {EVENT_CODE}-{SERIES_CODE}-{SEQUENCE}. " +
                    "Returns raw QR tokens ONLY on this call. Requires PASS_GENERATE permission."
    )
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

    @Operation(
            summary = "Activate a pass",
            description = "Atomically registers attendee info, activates the pass, and enqueues an activation email via transactional outbox. Single-use only."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pass activated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid QR token or wrong event"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Pass already activated or duplicate attendee email"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "Pass is revoked")
    })
    @PostMapping("/activate")
    public ResponseEntity<ApiResponse<PassResponse>> activatePass(
            @Valid @RequestBody ActivatePassRequest request) {
        PassResponse response = passActivationService.activatePass(request);
        return ResponseEntity.ok(ApiResponse.success("Pass activated successfully", response));
    }

    @Operation(summary = "List passes by event", description = "Retrieves paginated passes for an event. Raw tokens are never returned. Requires PASS_VIEW permission.")
    @GetMapping("/event/{eventId}")
    public ResponseEntity<ApiResponse<Page<PassResponse>>> listPasses(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(passGenerationService.listPassesByEvent(eventId, pageable)));
    }

    @Operation(summary = "Get pass details", description = "Retrieves pass details by pass UUID. Raw token is omitted. Requires PASS_VIEW permission.")
    @GetMapping("/{passId}")
    public ResponseEntity<ApiResponse<PassResponse>> getPass(
            @Parameter(description = "Pass UUID") @PathVariable UUID passId) {
        return ResponseEntity.ok(ApiResponse.success(passGenerationService.getPass(passId)));
    }
}
