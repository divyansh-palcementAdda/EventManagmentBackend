package com.app.eventmanagmentsystem.payload.user;

import com.app.eventmanagmentsystem.payload.role.RoleResponse;
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
public class UserResponse {

    private UUID id;
    private String email;
    private String fullName;
    private String mobileNumber;
    private String accountStatus;
    private List<RoleResponse> roles;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdByName;
    private String updatedByName;
}

