package com.app.eventmanagmentsystem.payload.permission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePermissionRequest {

    @NotBlank(message = "Permission name is required")
    @Size(max = 80, message = "Permission name cannot exceed 80 characters")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Permission name must contain only uppercase letters, numbers, and underscores")
    private String name;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    @NotBlank(message = "Resource is required")
    @Size(max = 60, message = "Resource cannot exceed 60 characters")
    private String resource;

    @NotBlank(message = "Action is required")
    @Size(max = 60, message = "Action cannot exceed 60 characters")
    private String action;
}

