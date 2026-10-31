package com.app.eventmanagmentsystem.payload.role;

import com.app.eventmanagmentsystem.payload.permission.PermissionResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {

    private UUID id;
    private String name;
    private String description;
    private boolean systemDefined;
    private int userCount;
    private int permissionCount;
    private List<String> permissionNames;
    private List<UUID> permissionIds;
    private List<PermissionResponse> permissions;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdByName;
    private String updatedByName;
}

