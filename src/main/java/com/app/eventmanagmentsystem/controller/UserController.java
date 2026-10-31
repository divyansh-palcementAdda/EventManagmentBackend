package com.app.eventmanagmentsystem.controller;

import com.app.eventmanagmentsystem.model.enums.AccountStatus;
import com.app.eventmanagmentsystem.payload.ApiResponse;
import com.app.eventmanagmentsystem.payload.user.*;
import com.app.eventmanagmentsystem.service.impl.UserService;
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

import java.util.UUID;

@Tag(name = "Users", description = "Endpoints for user management, role assignments, and profile operations")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get paginated users", description = "Searches and filters system users with sorting and pagination. Requires USER_VIEW permission.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getUsers(
            @Parameter(description = "Search query by name, email, or mobile") @RequestParam(required = false) String search,
            @Parameter(description = "Filter by account status") @RequestParam(required = false) AccountStatus status,
            @Parameter(description = "Filter by assigned role name") @RequestParam(required = false) String role,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<UserResponse> users = userService.getUsers(search, status, role, pageable);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @Operation(summary = "Get user by ID", description = "Retrieves user profile and assigned roles by UUID. Requires USER_VIEW permission.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @Parameter(description = "User UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(id)));
    }

    @Operation(summary = "Create user", description = "Creates a new system administrator or staff user. Requires USER_CREATE permission.")
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", response));
    }

    @Operation(summary = "Update user", description = "Updates an existing user's details and roles. Requires USER_UPDATE permission.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @Parameter(description = "User UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse response = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", response));
    }

    @Operation(summary = "Update user account status", description = "Activates, suspends, or disables a user account. Requires USER_DISABLE permission.")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
            @Parameter(description = "User UUID") @PathVariable UUID id,
            @Parameter(description = "New account status") @RequestParam AccountStatus status) {
        UserResponse response = userService.updateUserStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Account status updated to " + status, response));
    }

    @Operation(summary = "Assign roles to user", description = "Replaces the user's assigned roles. Requires ROLE_ASSIGN permission.")
    @PostMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<UserResponse>> assignRoles(
            @Parameter(description = "User UUID") @PathVariable UUID id,
            @Valid @RequestBody AssignRolesRequest request) {
        UserResponse response = userService.assignRoles(id, request);
        return ResponseEntity.ok(ApiResponse.success("Roles updated successfully", response));
    }

    @Operation(summary = "Remove role from user", description = "Removes a specific role from a user. Requires ROLE_ASSIGN permission.")
    @DeleteMapping("/{id}/roles/{roleId}")
    public ResponseEntity<ApiResponse<UserResponse>> removeRole(
            @Parameter(description = "User UUID") @PathVariable UUID id,
            @Parameter(description = "Role UUID") @PathVariable UUID roleId) {
        UserResponse response = userService.removeRole(id, roleId);
        return ResponseEntity.ok(ApiResponse.success("Role removed successfully", response));
    }

    @Operation(summary = "Admin reset password", description = "Resets a user's password and revokes all active sessions. Requires USER_UPDATE permission.")
    @PutMapping("/{id}/password")
    public ResponseEntity<ApiResponse<Void>> adminResetPassword(
            @Parameter(description = "User UUID") @PathVariable UUID id,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        userService.adminResetPassword(id, request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully"));
    }

    @Operation(summary = "Delete / suspend user", description = "Suspends a user account and revokes their active sessions. Requires USER_DISABLE permission.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @Parameter(description = "User UUID") @PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User suspended successfully"));
    }
}

