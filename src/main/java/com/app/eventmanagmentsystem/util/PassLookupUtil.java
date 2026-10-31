package com.app.eventmanagmentsystem.util;

import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.repository.EventPassRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility to extract pass identifiers from diverse scanned QR payloads
 * (URLs, query parameters, JSON, raw pass numbers, or token hashes)
 * and resolve the corresponding EventPass entity safely.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PassLookupUtil {

    private final EventPassRepository passRepository;
    private final QrTokenUtil qrTokenUtil;

    private static final Pattern QUERY_PARAM_PATTERN =
            Pattern.compile("[?&](?:passNumber|token|rawToken|passId)=([^&]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern PATH_ACTIVATE_PATTERN =
            Pattern.compile("/activate/([^/?#]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern PATH_PASS_PATTERN =
            Pattern.compile("/pass/(?:scan/)?([^/?#]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern JSON_PROPERTY_PATTERN =
            Pattern.compile("\"(?:passNumber|token|rawToken|passId)\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private static final Pattern EVENT_ID_PATTERN =
            Pattern.compile("[?&]eventId=([^&]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Extracts the primary pass identifier from a raw scanned string.
     */
    public String extractIdentifier(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return "";
        }
        String input = rawInput.trim();

        // 1. JSON payload check
        if (input.startsWith("{") && input.endsWith("}")) {
            Matcher jsonMatcher = JSON_PROPERTY_PATTERN.matcher(input);
            if (jsonMatcher.find()) {
                return jsonMatcher.group(1).trim();
            }
        }

        // 2. URL payload check
        if (input.startsWith("http://") || input.startsWith("https://") || input.contains("/activate") || input.contains("/pass/")) {
            // Check query params first
            Matcher queryMatcher = QUERY_PARAM_PATTERN.matcher(input);
            if (queryMatcher.find()) {
                try {
                    return URLDecoder.decode(queryMatcher.group(1), StandardCharsets.UTF_8).trim();
                } catch (Exception ignored) {
                    return queryMatcher.group(1).trim();
                }
            }

            // Check /activate/{id}
            Matcher pathMatcher = PATH_ACTIVATE_PATTERN.matcher(input);
            if (pathMatcher.find()) {
                try {
                    return URLDecoder.decode(pathMatcher.group(1), StandardCharsets.UTF_8).trim();
                } catch (Exception ignored) {
                    return pathMatcher.group(1).trim();
                }
            }

            // Check /pass/{id} or /pass/scan/{id}
            Matcher passPathMatcher = PATH_PASS_PATTERN.matcher(input);
            if (passPathMatcher.find()) {
                try {
                    return URLDecoder.decode(passPathMatcher.group(1), StandardCharsets.UTF_8).trim();
                } catch (Exception ignored) {
                    return passPathMatcher.group(1).trim();
                }
            }
        }

        return input;
    }

    /**
     * Extracts the optional event UUID if encoded in URL parameters or JSON.
     */
    public UUID extractEventId(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return null;
        }
        Matcher m = EVENT_ID_PATTERN.matcher(rawInput);
        if (m.find()) {
            try {
                String decoded = URLDecoder.decode(m.group(1), StandardCharsets.UTF_8).trim();
                return UUID.fromString(decoded);
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Resolves an EventPass in read-only mode by:
     * 1. Direct pass number (e.g. TEST123-SZ83KSCE-0001)
     * 2. SHA-256 hash of the input identifier
     * 3. Direct token hash
     * 4. Pass primary key UUID
     */
    public Optional<EventPass> findPass(String rawInput) {
        String identifier = extractIdentifier(rawInput);
        if (identifier.isBlank()) {
            return Optional.empty();
        }

        // 1. By Pass Number
        Optional<EventPass> byPassNumber = passRepository.findByPassNumber(identifier);
        if (byPassNumber.isPresent()) {
            return byPassNumber;
        }

        // 2. By Hashed Token (if rawToken was passed)
        String tokenHash = qrTokenUtil.hashToken(identifier);
        Optional<EventPass> byTokenHash = passRepository.findByTokenHash(tokenHash);
        if (byTokenHash.isPresent()) {
            return byTokenHash;
        }

        // 3. By Direct Token Hash (in case pre-hashed token was passed)
        Optional<EventPass> byDirectHash = passRepository.findByTokenHash(identifier);
        if (byDirectHash.isPresent()) {
            return byDirectHash;
        }

        // 4. By UUID
        try {
            UUID uuid = UUID.fromString(identifier);
            return passRepository.findById(uuid);
        } catch (IllegalArgumentException ignored) {
            // Not a UUID format
        }

        return Optional.empty();
    }

    /**
     * Resolves an EventPass with PESSIMISTIC_WRITE lock for concurrency-safe mutations.
     */
    public Optional<EventPass> findPassForUpdate(String rawInput) {
        String identifier = extractIdentifier(rawInput);
        if (identifier.isBlank()) {
            return Optional.empty();
        }

        // 1. By Pass Number
        Optional<EventPass> byPassNumber = passRepository.findByPassNumberForUpdate(identifier);
        if (byPassNumber.isPresent()) {
            return byPassNumber;
        }

        // 2. By Hashed Token
        String tokenHash = qrTokenUtil.hashToken(identifier);
        Optional<EventPass> byTokenHash = passRepository.findByTokenHashForUpdate(tokenHash);
        if (byTokenHash.isPresent()) {
            return byTokenHash;
        }

        // 3. By Direct Token Hash
        Optional<EventPass> byDirectHash = passRepository.findByTokenHashForUpdate(identifier);
        if (byDirectHash.isPresent()) {
            return byDirectHash;
        }

        // 4. By UUID
        try {
            UUID uuid = UUID.fromString(identifier);
            return passRepository.findByIdForUpdate(uuid);
        } catch (IllegalArgumentException ignored) {
            // Not a UUID format
        }

        return Optional.empty();
    }
}

