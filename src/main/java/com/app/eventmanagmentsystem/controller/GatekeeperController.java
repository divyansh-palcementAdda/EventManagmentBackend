package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.service.impl.GatekeeperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Gatekeepers", description = "Endpoints for managing event gatekeeper assignments")
@RestController
@RequestMapping("/api/v1/events/{eventId}/gatekeepers")
@RequiredArgsConstructor
public class GatekeeperController {

    private final GatekeeperService gatekeeperService;

    @Operation(summary = "Assign gatekeeper to event", description = "Assigns a system user with GATEKEEPER role to an event. Requires GATEKEEPER_ASSIGN permission.")
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
