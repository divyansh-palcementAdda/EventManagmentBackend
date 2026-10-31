package com.app.eventmanagmentsystem.payload.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private long expiresIn;
    private String refreshToken;
    private UUID userId;
    private String email;
    private String fullName;
    private UUID roleId;
    private String roleName;
    private Set<String> roles;
}
