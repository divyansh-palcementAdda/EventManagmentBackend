package com.app.eventmanagmentsystem.security.jwt;

import io.jsonwebtoken.Claims;
import lombok.Getter;

/**
 * Result of JWT token validation.
 */
@Getter
public class JwtValidationResult {

    private final boolean valid;
    private final boolean expired;
    private final String errorMessage;
    private final Claims claims;

    private JwtValidationResult(boolean valid, boolean expired, String errorMessage, Claims claims) {
        this.valid = valid;
        this.expired = expired;
        this.errorMessage = errorMessage;
        this.claims = claims;
    }

    public static JwtValidationResult valid(Claims claims) {
        return new JwtValidationResult(true, false, null, claims);
    }

    public static JwtValidationResult expired() {
        return new JwtValidationResult(false, true, "Token expired", null);
    }

    public static JwtValidationResult invalid(String message) {
        return new JwtValidationResult(false, false, message, null);
    }
}

