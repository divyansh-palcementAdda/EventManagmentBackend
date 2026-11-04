package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.model.RegistrationFormVersion;
import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.category.FormFieldDto;
import com.app.eventmanagmentsystem.payload.category.FormSchemaResponse;
import com.app.eventmanagmentsystem.payload.category.PublishFormVersionResponse;
import com.app.eventmanagmentsystem.service.impl.FormBuilderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Form Builder", description = "Endpoints for dynamic registration form builder schema design and versioning")
@RestController
@RequestMapping("/api/v1/event-categories/{eventCategoryId}/form")
@RequiredArgsConstructor
public class FormBuilderController {

    private final FormBuilderService formBuilderService;

    @Operation(summary = "Get draft form fields", description = "Retrieves current draft form fields for designing registration schema. Returns default fields if none yet customized.")
    @GetMapping("/draft")
    public ResponseEntity<ApiResponse<List<FormFieldDto>>> getDraftFields(
            @Parameter(description = "Event Pass Category UUID") @PathVariable UUID eventCategoryId) {
        List<FormFieldDto> fields = formBuilderService.getDraftFields(eventCategoryId);
        return ResponseEntity.ok(ApiResponse.success(fields));
    }

    @Operation(summary = "Save draft form fields", description = "Saves and reorders draft fields before publishing.")
    @PutMapping("/draft")
    public ResponseEntity<ApiResponse<List<FormFieldDto>>> saveDraftFields(
            @Parameter(description = "Event Pass Category UUID") @PathVariable UUID eventCategoryId,
            @RequestBody List<FormFieldDto> fieldDtos) {
        List<FormFieldDto> saved = formBuilderService.saveDraftFields(eventCategoryId, fieldDtos);
        return ResponseEntity.ok(ApiResponse.success("Draft form fields saved successfully", saved));
    }

    @Operation(summary = "Delete a form field from draft", description = "Deletes an individual field from the draft configuration.")
    @DeleteMapping("/fields/{fieldConfigId}")
    public ResponseEntity<ApiResponse<Void>> deleteField(
            @Parameter(description = "Event Pass Category UUID") @PathVariable UUID eventCategoryId,
            @Parameter(description = "Form Field Config UUID") @PathVariable UUID fieldConfigId) {
        formBuilderService.deleteField(fieldConfigId);
        return ResponseEntity.ok(ApiResponse.success("Field deleted from draft", null));
    }

    @Operation(summary = "Publish form schema version", description = "Takes an immutable snapshot of current draft fields, increments the version number, and sets as active published version.")
    @PostMapping("/publish")
    public ResponseEntity<ApiResponse<PublishFormVersionResponse>> publishVersion(
            @Parameter(description = "Event Pass Category UUID") @PathVariable UUID eventCategoryId,
            @RequestBody(required = false) Map<String, String> body) {
        String changelog = body != null ? body.get("changelog") : null;
        PublishFormVersionResponse response = formBuilderService.publishFormVersion(eventCategoryId, changelog);
        return ResponseEntity.ok(ApiResponse.success("Form schema version published successfully", response));
    }

    @Operation(summary = "Get published form schema", description = "Retrieves the currently published schema with deserialized form fields.")
    @GetMapping("/published")
    public ResponseEntity<ApiResponse<FormSchemaResponse>> getPublishedSchema(
            @Parameter(description = "Event Pass Category UUID") @PathVariable UUID eventCategoryId) {
        FormSchemaResponse response = formBuilderService.getPublishedFormSchema(eventCategoryId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get form version history", description = "Retrieves all published version snapshots for audit and historical records.")
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<RegistrationFormVersion>>> getVersionHistory(
            @Parameter(description = "Event Pass Category UUID") @PathVariable UUID eventCategoryId) {
        List<RegistrationFormVersion> history = formBuilderService.getVersionHistory(eventCategoryId);
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}

