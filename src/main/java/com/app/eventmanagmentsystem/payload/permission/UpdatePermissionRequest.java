package com.app.eventmanagmentsystem.payload.permission;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePermissionRequest {

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    @Size(max = 60, message = "Resource cannot exceed 60 characters")
    private String resource;

    @Size(max = 60, message = "Action cannot exceed 60 characters")
    private String action;
}

