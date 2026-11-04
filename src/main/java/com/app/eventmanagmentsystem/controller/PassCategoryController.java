package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.model.enums.CategoryScope;
import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.category.CreatePassCategoryRequest;
import com.app.eventmanagmentsystem.payload.category.PassCategoryResponse;
import com.app.eventmanagmentsystem.payload.category.UpdatePassCategoryRequest;
import com.app.eventmanagmentsystem.service.impl.PassCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Pass Categories", description = "Endpoints for managing global and event-specific pass categories")
@RestController
@RequestMapping("/api/v1/pass-categories")
@RequiredArgsConstructor
public class PassCategoryController {

    private final PassCategoryService passCategoryService;

    @Operation(summary = "List pass categories", description = "Retrieves paginated pass categories with filters for scope, event, active state, and search query.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PassCategoryResponse>>> listCategories(
            @RequestParam(required = false) CategoryScope scope,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "displayOrder") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(direction, sortBy));

        Page<PassCategoryResponse> result = passCategoryService.listCategories(scope, eventId, active, search, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Get available categories for an event", description = "Returns all global categories and event-specific categories available to associate with an event.")
    @GetMapping("/available-for-event/{eventId}")
    public ResponseEntity<ApiResponse<List<PassCategoryResponse>>> getAvailableForEvent(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId) {
        List<PassCategoryResponse> list = passCategoryService.getAvailableForEvent(eventId);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @Operation(summary = "Get pass category by ID", description = "Retrieves a single pass category details.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PassCategoryResponse>> getCategory(
            @Parameter(description = "Category UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(passCategoryService.getCategoryById(id)));
    }

    @Operation(summary = "Create a new pass category", description = "Creates a GLOBAL or EVENT_SPECIFIC pass category.")
    @PostMapping
    public ResponseEntity<ApiResponse<PassCategoryResponse>> createCategory(
            @Valid @RequestBody CreatePassCategoryRequest request) {
        PassCategoryResponse response = passCategoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Pass category created successfully", response));
    }

    @Operation(summary = "Update a pass category", description = "Updates category name, description, and display order.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PassCategoryResponse>> updateCategory(
            @Parameter(description = "Category UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdatePassCategoryRequest request) {
        PassCategoryResponse response = passCategoryService.updateCategory(id, request);
        return ResponseEntity.ok(ApiResponse.success("Pass category updated successfully", response));
    }

    @Operation(summary = "Toggle pass category active state", description = "Enables or disables a pass category.")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<ApiResponse<PassCategoryResponse>> toggleCategoryStatus(
            @Parameter(description = "Category UUID") @PathVariable UUID id) {
        PassCategoryResponse response = passCategoryService.toggleCategoryStatus(id);
        return ResponseEntity.ok(ApiResponse.success("Pass category status updated successfully", response));
    }
}

