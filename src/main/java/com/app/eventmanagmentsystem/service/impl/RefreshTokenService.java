package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.model.RefreshToken;
import com.app.eventmanagmentsystem.model.User;
import com.app.eventmanagmentsystem.payload.auth.LoginResponse;
import com.app.eventmanagmentsystem.repository.RefreshTokenRepository;
import com.app.eventmanagmentsystem.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.jwt.refresh-token-expiry-ms:604800000}")
    private long refreshTokenExpiryMs;

    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = generateSecureRandomToken();
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(Instant.now().plusMillis(refreshTokenExpiryMs));

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional(noRollbackFor = InvalidRequestException.class)
    public LoginResponse rotateRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRequestException("Refresh token is required.");
        }

        String tokenHash = hashToken(rawRefreshToken.trim());
        RefreshToken existingToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidRequestException("Invalid refresh token."));

        User user = existingToken.getUser();

        // Check for token reuse (if token was already rotated or revoked)
        if (existingToken.isRevoked() || existingToken.getReplacedByTokenHash() != null) {
            log.warn("Security Alert: Refresh token reuse detected for user ID: {}. Revoking all sessions.", user.getId());
            refreshTokenRepository.revokeAllActiveTokensForUser(
                    user.getId(), Instant.now(), "Token reuse detected");
            throw new InvalidRequestException("Invalid or previously rotated refresh token. Please login again.");
        }

        if (existingToken.isExpired()) {
            existingToken.setRevokedAt(Instant.now());
            existingToken.setRevocationReason("EXPIRED");
            refreshTokenRepository.save(existingToken);
            throw new InvalidRequestException("Refresh token expired. Please login again.");
        }

        if (!user.isEnabled()) {
            throw new InvalidRequestException("User account is inactive or disabled.");
        }

        // Issue new rotated token
        String newRawToken = generateSecureRandomToken();
        String newTokenHash = hashToken(newRawToken);

        existingToken.setRevokedAt(Instant.now());
        existingToken.setRevocationReason("ROTATED");
        existingToken.setReplacedByTokenHash(newTokenHash);
        refreshTokenRepository.save(existingToken);

        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUser(user);
        newRefreshToken.setTokenHash(newTokenHash);
        newRefreshToken.setExpiresAt(Instant.now().plusMillis(refreshTokenExpiryMs));
        refreshTokenRepository.save(newRefreshToken);

        Set<String> roles = user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.toSet());

        Set<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getName())
                .collect(Collectors.toSet());

        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                Map.of("permissions", permissions, "roles", roles)
        );

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiryMs())
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    @Transactional
    public void revokeRefreshToken(String rawRefreshToken, String reason) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash = hashToken(rawRefreshToken.trim());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.setRevokedAt(Instant.now());
                token.setRevocationReason(reason != null ? reason : "LOGOUT");
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional
    public void revokeAllUserTokens(UUID userId, String reason) {
        refreshTokenRepository.revokeAllActiveTokensForUser(userId, Instant.now(), reason);
    }

    @Scheduled(cron = "${app.jwt.refresh-token-cleanup-cron:0 0 3 * * ?}")
    @Transactional
    public void cleanupExpiredTokens() {
        // Delete tokens that expired or were revoked more than 30 days ago
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        int deleted = refreshTokenRepository.deleteExpiredOrRevokedBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} expired/revoked refresh tokens older than 30 days.", deleted);
        }
    }

    private String generateSecureRandomToken() {
        byte[] bytes = new byte[32]; // 256 bits
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

