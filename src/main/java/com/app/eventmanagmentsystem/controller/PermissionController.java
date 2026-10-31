package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.permission.CreatePermissionRequest;
import com.app.eventmanagmentsystem.payload.permission.PermissionResponse;
import com.app.eventmanagmentsystem.payload.permission.UpdatePermissionRequest;
import com.app.eventmanagmentsystem.service.impl.PermissionService;
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

@Tag(name = "Permissions", description = "Endpoints for permission catalog inspection and management")
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @Operation(summary = "Get paginated permissions", description = "Searches and filters permissions by resource or name. Requires PERMISSION_VIEW permission.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PermissionResponse>>> getPermissions(
            @Parameter(description = "Search query by name, description, or action") @RequestParam(required = false) String search,
            @Parameter(description = "Filter by resource category") @RequestParam(required = false) String resource,
            @PageableDefault(size = 50, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getPermissions(search, resource, pageable)));
    }

    @Operation(summary = "Get all permissions list", description = "Returns all available permissions in a simple list. Requires PERMISSION_VIEW permission.")
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getAllPermissions()));
    }

    @Operation(summary = "Get distinct resource categories", description = "Returns unique permission resource categories. Requires PERMISSION_VIEW permission.")
    @GetMapping("/resources")
    public ResponseEntity<ApiResponse<List<String>>> getDistinctResources() {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getDistinctResources()));
    }

    @Operation(summary = "Get permission by ID", description = "Retrieves permission details by UUID. Requires PERMISSION_VIEW permission.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponse>> getPermissionById(
            @Parameter(description = "Permission UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getPermissionById(id)));
    }

    @Operation(summary = "Create permission", description = "Creates a new atomic permission. Requires PERMISSION_MANAGE permission.")
    @PostMapping
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(
            @Valid @RequestBody CreatePermissionRequest request) {
        PermissionResponse response = permissionService.createPermission(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Permission created successfully", response));
    }

    @Operation(summary = "Update permission metadata", description = "Updates permission description, resource, or action. Requires PERMISSION_MANAGE permission.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponse>> updatePermission(
            @Parameter(description = "Permission UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdatePermissionRequest request) {
        PermissionResponse response = permissionService.updatePermission(id, request);
        return ResponseEntity.ok(ApiResponse.success("Permission updated successfully", response));
    }
}

