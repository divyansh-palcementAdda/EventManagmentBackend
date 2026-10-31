package com.app.eventmanagmentsystem.payload.role;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoleRequest {

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    private Set<UUID> permissionIds;
}

