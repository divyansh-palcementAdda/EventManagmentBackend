package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.checkin.CheckInRequest;
import com.app.eventmanagmentsystem.payload.checkin.CheckInResponse;
import com.app.eventmanagmentsystem.service.impl.CheckInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Check-In", description = "Endpoints for gatekeeper check-in scan and event entry verification")
@RestController
@RequestMapping("/api/v1/checkin")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @Operation(
            summary = "Perform check-in entry scan",
            description = "Validates the QR token, ensures the pass is ACTIVATED and not revoked, verifies the authenticated gatekeeper is assigned to the event, and records a single-use CheckIn entry. Requires CHECKIN_SCAN permission."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Check-in successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid QR token or pass belongs to different event"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Gatekeeper is not assigned to this event"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Pass has already been checked in"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "Pass has been revoked"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Pass has not been activated yet")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<CheckInResponse>> checkIn(@Valid @RequestBody CheckInRequest request) {
        CheckInResponse response = checkInService.performCheckIn(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Check-in successful", response));
    }
}
