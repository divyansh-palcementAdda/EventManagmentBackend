package com.app.eventmanagmentsystem.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Creates and validates JWT access tokens.
 *
 * <p>The signing secret MUST be provided via the environment variable
 * {@code EVENTPASS_JWT_SECRET}. The application will fail at startup if the
 * secret is missing or insufficient (see {@link JwtSecretValidator}).
 *
 * <p>Raw tokens are never logged.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;
    private final String issuer;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiry-ms:900000}") long accessTokenExpiryMs,
            @Value("${app.jwt.issuer:eventpass}") String issuer) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.issuer = issuer;
    }

    public String generateAccessToken(UUID userId, String email, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("email", email)
                .claims(extraClaims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenExpiryMs)))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Returns null if invalid (caller handles exception types).
     */
    public JwtValidationResult validate(String token) {
        try {
            Claims claims = parseAndValidate(token);
            return JwtValidationResult.valid(claims);
        } catch (ExpiredJwtException e) {
            return JwtValidationResult.expired();
        } catch (SignatureException e) {
            return JwtValidationResult.invalid("Invalid signature");
        } catch (MalformedJwtException e) {
            return JwtValidationResult.invalid("Malformed token");
        } catch (JwtException | IllegalArgumentException e) {
            return JwtValidationResult.invalid("Token validation failed");
        }
    }

    public long getAccessTokenExpiryMs() {
        return accessTokenExpiryMs;
    }
}

