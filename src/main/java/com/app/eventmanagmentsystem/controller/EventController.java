package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.model.enums.EventStatus;
import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.event.CreateEventRequest;
import com.app.eventmanagmentsystem.payload.event.EventResponse;
import com.app.eventmanagmentsystem.service.impl.EventService;
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

import java.util.UUID;

@Tag(name = "Events", description = "Endpoints for managing events, policies, and publication status")
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @Operation(summary = "Create an event", description = "Creates a new event. Event code is optional (auto-generated if omitted). Requires EVENT_CREATE permission.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Event successfully created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Event code already exists")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(@Valid @RequestBody CreateEventRequest request) {
        EventResponse response = eventService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Event created", response));
    }

    @Operation(summary = "Get event by ID", description = "Retrieves event details including total passes generated. Requires EVENT_VIEW permission.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Event details found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Event not found")
    })
    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventResponse>> getEvent(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getEvent(eventId)));
    }

    @Operation(summary = "List events", description = "Lists events with pagination. Requires EVENT_VIEW permission.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<EventResponse>>> listEvents(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(eventService.listEvents(pageable)));
    }

    @Operation(summary = "Update event status", description = "Transitions event status (DRAFT, PUBLISHED, ACTIVE, COMPLETED, CANCELLED). Requires EVENT_UPDATE permission.")
    @PatchMapping("/{eventId}/status")
    public ResponseEntity<ApiResponse<EventResponse>> updateStatus(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Parameter(description = "Target status") @RequestParam EventStatus status) {
        return ResponseEntity.ok(ApiResponse.success("Status updated", eventService.updateEventStatus(eventId, status)));
    }
}
