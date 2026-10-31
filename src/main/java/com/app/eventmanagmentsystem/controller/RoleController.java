package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.role.AssignPermissionsRequest;
import com.app.eventmanagmentsystem.payload.role.CreateRoleRequest;
import com.app.eventmanagmentsystem.payload.role.RoleResponse;
import com.app.eventmanagmentsystem.payload.role.UpdateRoleRequest;
import com.app.eventmanagmentsystem.payload.user.UserResponse;
import com.app.eventmanagmentsystem.service.impl.RoleService;
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

@Tag(name = "Roles", description = "Endpoints for dynamic role management and permission assignments")
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "Get paginated roles", description = "Searches and filters roles with pagination. Requires ROLE_VIEW permission.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<RoleResponse>>> getRoles(
            @Parameter(description = "Search query by name or description") @RequestParam(required = false) String search,
            @Parameter(description = "Filter by system-defined status") @RequestParam(required = false) Boolean systemDefined,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(roleService.getRoles(search, systemDefined, pageable)));
    }

    @Operation(summary = "Get all roles list", description = "Returns all available roles in a simple list for dropdowns. Requires ROLE_VIEW permission.")
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        return ResponseEntity.ok(ApiResponse.success(roleService.getAllRoles()));
    }

    @Operation(summary = "Get role by ID", description = "Retrieves role details and assigned permissions by UUID. Requires ROLE_VIEW permission.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(
            @Parameter(description = "Role UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(roleService.getRoleById(id)));
    }

    @Operation(summary = "Create role", description = "Creates a new dynamic role. Requires ROLE_CREATE permission.")
    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleRequest request) {
        RoleResponse response = roleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Role created successfully", response));
    }

    @Operation(summary = "Update role", description = "Updates role description and assigned permissions. Requires ROLE_UPDATE permission.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @Parameter(description = "Role UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request) {
        RoleResponse response = roleService.updateRole(id, request);
        return ResponseEntity.ok(ApiResponse.success("Role updated successfully", response));
    }

    @Operation(summary = "Delete role", description = "Deletes a custom role if not system-defined and has 0 assigned users. Requires ROLE_DELETE permission.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @Parameter(description = "Role UUID") @PathVariable UUID id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success("Role deleted successfully"));
    }

    @Operation(summary = "Add permissions to role", description = "Appends permissions to an existing role. Requires ROLE_UPDATE permission.")
    @PostMapping("/{id}/permissions")
    public ResponseEntity<ApiResponse<RoleResponse>> assignPermissions(
            @Parameter(description = "Role UUID") @PathVariable UUID id,
            @Valid @RequestBody AssignPermissionsRequest request) {
        RoleResponse response = roleService.assignPermissions(id, request);
        return ResponseEntity.ok(ApiResponse.success("Permissions assigned successfully", response));
    }

    @Operation(summary = "Replace role permissions", description = "Atomically replaces all permissions for a role. Requires ROLE_UPDATE permission.")
    @PutMapping("/{id}/permissions")
    public ResponseEntity<ApiResponse<RoleResponse>> replacePermissions(
            @Parameter(description = "Role UUID") @PathVariable UUID id,
            @Valid @RequestBody AssignPermissionsRequest request) {
        RoleResponse response = roleService.replacePermissions(id, request);
        return ResponseEntity.ok(ApiResponse.success("Permissions replaced successfully", response));
    }

    @Operation(summary = "Remove permission from role", description = "Removes an individual permission from a role. Requires ROLE_UPDATE permission.")
    @DeleteMapping("/{id}/permissions/{permissionId}")
    public ResponseEntity<ApiResponse<RoleResponse>> removePermission(
            @Parameter(description = "Role UUID") @PathVariable UUID id,
            @Parameter(description = "Permission UUID") @PathVariable UUID permissionId) {
        RoleResponse response = roleService.removePermission(id, permissionId);
        return ResponseEntity.ok(ApiResponse.success("Permission removed successfully", response));
    }

    @Operation(summary = "Get users assigned to role", description = "Returns paginated list of users who have this role. Requires ROLE_VIEW permission.")
    @GetMapping("/{id}/users")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getRoleUsers(
            @Parameter(description = "Role UUID") @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(roleService.getRoleUsers(id, pageable)));
    }
}

