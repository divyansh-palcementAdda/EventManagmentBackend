package com.app.eventmanagmentsystem.payload.auth;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;
import java.util.UUID;

@Getter
@Builder
public class LoginResponse {

    private final String accessToken;
    private final String tokenType = "Bearer";
    private final long expiresIn;
    private final UUID userId;
    private final String email;
    private final String fullName;
    private final Set<String> permissions;
}

