package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.category.ConfigureEventCategoryRequest;
import com.app.eventmanagmentsystem.payload.category.EventPassCategoryResponse;
import com.app.eventmanagmentsystem.payload.category.UpdateEventCategoryPolicyRequest;
import com.app.eventmanagmentsystem.service.impl.EventPassCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Event Pass Categories", description = "Endpoints for configuring and managing category policies per event")
@RestController
@RequestMapping("/api/v1/events/{eventId}/categories")
@RequiredArgsConstructor
public class EventPassCategoryController {

    private final EventPassCategoryService eventPassCategoryService;

    @Operation(summary = "Get configured categories for an event", description = "Retrieves all categories configured for an event with their activation policies.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<EventPassCategoryResponse>>> getCategoriesForEvent(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @RequestParam(required = false) Boolean enabledOnly) {
        List<EventPassCategoryResponse> list = eventPassCategoryService.getCategoriesForEvent(eventId, enabledOnly);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @Operation(summary = "Configure a category for an event", description = "Associates a category with an event, setting activation mode, QR requirement, and identity policy.")
    @PostMapping
    public ResponseEntity<ApiResponse<EventPassCategoryResponse>> configureCategory(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Valid @RequestBody ConfigureEventCategoryRequest request) {
        EventPassCategoryResponse response = eventPassCategoryService.configureCategoryForEvent(eventId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category configured for event successfully", response));
    }

    @Operation(summary = "Get an event-category configuration by ID", description = "Retrieves an individual event-category policy.")
    @GetMapping("/{eventCategoryId}")
    public ResponseEntity<ApiResponse<EventPassCategoryResponse>> getEventCategory(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Parameter(description = "Event Pass Category UUID") @PathVariable UUID eventCategoryId) {
        return ResponseEntity.ok(ApiResponse.success(eventPassCategoryService.getEventCategoryById(eventCategoryId)));
    }

    @Operation(summary = "Update event-category policies", description = "Updates activation mode, QR policy, identity policy, and enabled status for an event category.")
    @PutMapping("/{eventCategoryId}")
    public ResponseEntity<ApiResponse<EventPassCategoryResponse>> updatePolicy(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Parameter(description = "Event Pass Category UUID") @PathVariable UUID eventCategoryId,
            @Valid @RequestBody UpdateEventCategoryPolicyRequest request) {
        EventPassCategoryResponse response = eventPassCategoryService.updateCategoryPolicy(eventCategoryId, request);
        return ResponseEntity.ok(ApiResponse.success("Category policy updated successfully", response));
    }

    @Operation(summary = "Toggle event category active state", description = "Enables or disables a category within the scope of an event.")
    @PatchMapping("/{eventCategoryId}/toggle-active")
    public ResponseEntity<ApiResponse<EventPassCategoryResponse>> toggleActive(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Parameter(description = "Event Pass Category UUID") @PathVariable UUID eventCategoryId) {
        EventPassCategoryResponse response = eventPassCategoryService.toggleCategoryActive(eventCategoryId);
        return ResponseEntity.ok(ApiResponse.success("Event category active status toggled", response));
    }
}

