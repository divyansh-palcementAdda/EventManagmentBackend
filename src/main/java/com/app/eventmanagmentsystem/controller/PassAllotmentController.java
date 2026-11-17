package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.allotment.*;
import com.app.eventmanagmentsystem.service.impl.PassAllotmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Pass Allotments", description = "Endpoints for managing internal pass allotments, sequences, and category analytics")
@RestController
@RequestMapping("/api/v1/events/{eventId}/pass-allotments")
@RequiredArgsConstructor
public class PassAllotmentController {

    private final PassAllotmentService passAllotmentService;

    @Operation(summary = "Get event pass allotments summary", description = "Returns aggregated statistics and person-wise grouped pass allotment summary.")
    @GetMapping
    public ResponseEntity<ApiResponse<PassAllotmentSummaryResponse>> getAllotmentsSummary(@PathVariable("eventId") UUID eventId) {
        PassAllotmentSummaryResponse summary = passAllotmentService.getAllotmentsSummary(eventId);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @Operation(summary = "Get sequences for category", description = "Loads available sequence ranges belonging to the selected category.")
    @GetMapping("/sequences")
    public ResponseEntity<ApiResponse<List<PassSequenceOptionResponse>>> getSequencesForCategory(
            @PathVariable("eventId") UUID eventId,
            @RequestParam("categoryCode") String categoryCode) {
        List<PassSequenceOptionResponse> sequences = passAllotmentService.getSequencesForCategory(eventId, categoryCode);
        return ResponseEntity.ok(ApiResponse.success(sequences));
    }

    @Operation(summary = "Get sequence summary", description = "Returns total capacity, already allotted count, and available to allot count for a sequence.")
    @GetMapping("/sequence-summary")
    public ResponseEntity<ApiResponse<PassSequenceSummaryResponse>> getSequenceSummary(
            @PathVariable("eventId") UUID eventId,
            @RequestParam("categoryCode") String categoryCode,
            @RequestParam("fromSequence") Long fromSequence,
            @RequestParam("toSequence") Long toSequence) {
        PassSequenceSummaryResponse summary = passAllotmentService.getSequenceSummary(eventId, categoryCode, fromSequence, toSequence);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @Operation(summary = "Create pass allotment", description = "Allots a specified quantity of passes from a sequence to an internal person/department.")
    @PostMapping
    public ResponseEntity<ApiResponse<PassAllotmentResponse>> createAllotment(
            @PathVariable("eventId") UUID eventId,
            @Valid @RequestBody CreatePassAllotmentRequest request) {
        PassAllotmentResponse response = passAllotmentService.createAllotment(eventId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Passes successfully allotted to " + response.getAllottedTo(), response));
    }

    @Operation(summary = "Get category allotment analytics", description = "Returns category-wise allotment summary and paginated pass details for a recipient.")
    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<PassAllotmentAnalyticsResponse>> getCategoryAnalytics(
            @PathVariable("eventId") UUID eventId,
            @RequestParam("allottedTo") String allottedTo,
            @RequestParam("categoryCode") String categoryCode,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "activationStatus", required = false) PassStatus activationStatus,
            @RequestParam(value = "checkInStatus", required = false) String checkInStatus,
            @PageableDefault(size = 20, sort = "sequenceNumber", direction = Sort.Direction.ASC) Pageable pageable) {
        PassAllotmentAnalyticsResponse analytics = passAllotmentService.getCategoryAnalytics(
                eventId, allottedTo, categoryCode, search, activationStatus, checkInStatus, pageable);
        return ResponseEntity.ok(ApiResponse.success(analytics));
    }
}
