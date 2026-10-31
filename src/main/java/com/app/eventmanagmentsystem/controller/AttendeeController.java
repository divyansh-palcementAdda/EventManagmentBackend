package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.model.enums.RegistrationStatus;
import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.attendee.AttendeeResponse;
import com.app.eventmanagmentsystem.payload.attendee.AttendeeStatsResponse;
import com.app.eventmanagmentsystem.payload.attendee.CreateAttendeeRequest;
import com.app.eventmanagmentsystem.payload.attendee.UpdateAttendeeRequest;
import com.app.eventmanagmentsystem.service.impl.AttendeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@Tag(name = "Attendees", description = "Endpoints for attendee registration, lookup, update, and statistics")
@RestController
@RequestMapping("/api/v1/attendees")
@RequiredArgsConstructor
public class AttendeeController {

    private final AttendeeService attendeeService;

    @Operation(summary = "Get paginated attendees", description = "Searches and filters attendees by event, status, or check-in state. Requires ATTENDEE_VIEW permission.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AttendeeResponse>>> getAttendees(
            @Parameter(description = "Optional filter by Event UUID") @RequestParam(required = false) UUID eventId,
            @Parameter(description = "Search query by name, email, mobile, or org") @RequestParam(required = false) String search,
            @Parameter(description = "Filter by registration status") @RequestParam(required = false) RegistrationStatus status,
            @Parameter(description = "Filter by check-in state (CHECKED_IN / NOT_CHECKED_IN)") @RequestParam(required = false) String checkInStatus,
            @PageableDefault(size = 20, sort = "registeredAt") Pageable pageable) {

        Page<AttendeeResponse> response = attendeeService.getAttendees(eventId, search, status, checkInStatus, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get attendee by ID", description = "Retrieves attendee details, linked pass, and check-in history. Requires ATTENDEE_VIEW permission.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AttendeeResponse>> getAttendeeById(
            @Parameter(description = "Attendee UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(attendeeService.getAttendeeById(id)));
    }

    @Operation(summary = "Create / register attendee", description = "Registers an attendee for an event. Requires ATTENDEE_CREATE or EVENT_UPDATE permission.")
    @PostMapping
    public ResponseEntity<ApiResponse<AttendeeResponse>> createAttendee(
            @Valid @RequestBody CreateAttendeeRequest request) {
        AttendeeResponse response = attendeeService.createAttendee(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Attendee registered successfully", response));
    }

    @Operation(summary = "Update attendee details", description = "Updates attendee contact and profile details. Requires ATTENDEE_UPDATE permission.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AttendeeResponse>> updateAttendee(
            @Parameter(description = "Attendee UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateAttendeeRequest request) {
        AttendeeResponse response = attendeeService.updateAttendee(id, request);
        return ResponseEntity.ok(ApiResponse.success("Attendee updated successfully", response));
    }

    @Operation(summary = "Cancel attendee registration", description = "Cancels attendee registration status. Requires ATTENDEE_DELETE or EVENT_UPDATE permission.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAttendee(
            @Parameter(description = "Attendee UUID") @PathVariable UUID id) {
        attendeeService.deleteAttendee(id);
        return ResponseEntity.ok(ApiResponse.success("Attendee registration cancelled successfully"));
    }

    @Operation(summary = "Get attendee statistics for event", description = "Returns total, confirmed, cancelled, and checked-in counts for an event. Requires ATTENDEE_VIEW permission.")
    @GetMapping("/event/{eventId}/stats")
    public ResponseEntity<ApiResponse<AttendeeStatsResponse>> getAttendeeStats(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(attendeeService.getAttendeeStats(eventId)));
    }

    @Operation(summary = "Export event attendees", description = "Exports attendee list with pass identities for an event. Requires ATTENDEE_VIEW or ATTENDEE_EXPORT permission.")
    @GetMapping("/event/{eventId}/export")
    public ResponseEntity<ApiResponse<List<AttendeeResponse>>> exportAttendees(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(attendeeService.exportAttendees(eventId)));
    }
}

