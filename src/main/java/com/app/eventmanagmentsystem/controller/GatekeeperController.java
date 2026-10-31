package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.gatekeeper.AssignGatekeeperRequest;
import com.app.eventmanagmentsystem.payload.gatekeeper.CreateGatekeeperRequest;
import com.app.eventmanagmentsystem.payload.gatekeeper.GatekeeperResponse;
import com.app.eventmanagmentsystem.service.impl.GatekeeperService;
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

@Tag(name = "Gatekeepers", description = "Endpoints for managing event gatekeeper assignments")
@RestController
@RequestMapping("/api/v1/events/{eventId}/gatekeepers")
@RequiredArgsConstructor
public class GatekeeperController {

    private final GatekeeperService gatekeeperService;

    @Operation(summary = "List gatekeepers for an event", description = "Retrieves all assigned gatekeepers for the event. Requires EVENT_VIEW or GATEKEEPER_ASSIGN permission.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<GatekeeperResponse>>> getGatekeepers(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId) {
        List<GatekeeperResponse> responses = gatekeeperService.getEventGatekeepers(eventId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "Search eligible gatekeepers", description = "Searches users with GATEKEEPER role to assign to the event. Requires GATEKEEPER_ASSIGN permission.")
    @GetMapping("/eligible")
    public ResponseEntity<ApiResponse<Page<GatekeeperResponse>>> searchEligibleGatekeepers(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Parameter(description = "Search query by name or email") @RequestParam(required = false, defaultValue = "") String query,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<GatekeeperResponse> page = gatekeeperService.searchEligibleGatekeepers(eventId, query, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @Operation(summary = "Create and assign a new gatekeeper", description = "Creates a new user account with GATEKEEPER role and immediately assigns them to the event. Requires GATEKEEPER_ASSIGN permission.")
    @PostMapping("/create-and-assign")
    public ResponseEntity<ApiResponse<GatekeeperResponse>> createAndAssignGatekeeper(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Valid @RequestBody CreateGatekeeperRequest request) {
        GatekeeperResponse response = gatekeeperService.createAndAssignGatekeeper(eventId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Gatekeeper created and assigned successfully", response));
    }

    @Operation(summary = "Assign gatekeeper via JSON body", description = "Assigns an existing user to the event. Requires GATEKEEPER_ASSIGN permission.")
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> assignGatekeeperBody(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Valid @RequestBody AssignGatekeeperRequest request) {
        gatekeeperService.assignGatekeeper(eventId, request.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Gatekeeper assigned successfully"));
    }

    @Operation(summary = "Assign gatekeeper via path variable", description = "Assigns a system user with GATEKEEPER role to an event. Requires GATEKEEPER_ASSIGN permission.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Gatekeeper assigned successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "User does not have GATEKEEPER role"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Event or User not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "User already actively assigned to this event")
    })
    @PostMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> assignGatekeeper(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Parameter(description = "User UUID to assign as gatekeeper") @PathVariable UUID userId) {
        gatekeeperService.assignGatekeeper(eventId, userId);
        return ResponseEntity.ok(ApiResponse.success("Gatekeeper assigned successfully"));
    }

    @Operation(summary = "Remove gatekeeper from event", description = "Soft-deactivates the gatekeeper assignment, immediately revoking check-in access. Requires GATEKEEPER_REMOVE permission.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Gatekeeper removed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Active assignment not found")
    })
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeGatekeeper(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Parameter(description = "User UUID to unassign") @PathVariable UUID userId) {
        gatekeeperService.removeGatekeeper(eventId, userId);
        return ResponseEntity.ok(ApiResponse.success("Gatekeeper removed successfully"));
    }
}
