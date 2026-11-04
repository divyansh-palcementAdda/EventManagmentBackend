package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.model.enums.ActivationMode;
import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.category.EventPassCategoryResponse;
import com.app.eventmanagmentsystem.payload.walkin.WalkInAdmitRequest;
import com.app.eventmanagmentsystem.payload.walkin.WalkInAdmitResponse;
import com.app.eventmanagmentsystem.service.impl.EventPassCategoryService;
import com.app.eventmanagmentsystem.service.impl.WalkInAdmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Walk-in Admission", description = "Endpoints for authorized staff to admit attendees without pre-printed QR passes")
@RestController
@RequestMapping("/api/v1/walkin")
@RequiredArgsConstructor
public class WalkInController {

    private final WalkInAdmissionService walkInAdmissionService;
    private final EventPassCategoryService eventPassCategoryService;

    @Operation(summary = "Get walk-in categories for an event", description = "Returns active event-categories configured with NO_QR_WALK_IN activation mode.")
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<EventPassCategoryResponse>>> getWalkInCategories(
            @Parameter(description = "Event UUID") @RequestParam UUID eventId) {
        List<EventPassCategoryResponse> list = eventPassCategoryService.getCategoriesForEvent(eventId, true).stream()
                .filter(c -> c.getActivationMode() == ActivationMode.NO_QR_WALK_IN)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @Operation(summary = "Admit and check in walk-in attendee", description = "Allocates a non-QR pass, captures attendee registration info/dynamic form answers, and records immediate check-in.")
    @PostMapping("/admit")
    public ResponseEntity<ApiResponse<WalkInAdmitResponse>> admitAttendee(
            @Valid @RequestBody WalkInAdmitRequest request) {
        WalkInAdmitResponse response = walkInAdmissionService.admitAttendee(request);
        return ResponseEntity.ok(ApiResponse.success("Walk-in attendee admitted and checked in successfully", response));
    }
}

