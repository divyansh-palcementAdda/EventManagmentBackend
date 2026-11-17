package com.app.eventmanagmentsystem.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Generates cryptographically secure, unambiguous alphanumeric codes for individual passes.
 *
 * <p>Format: 8 uppercase characters drawn from the 32-character unambiguous set:
 * {@code 23456789ABCDEFGHJKLMNPQRSTUVWXYZ}.
 *
 * <p>Visually confusing characters (0, O, 1, I) are excluded to prevent manual entry and
 * transcription errors on printed badges and physical passes.
 *
 * <p>Total permutations for length 8: 32^8 = 1,099,511,627,776 (&gt; 1.09 trillion).
 */
@Component
public class PassCodeGenerator {

    /**
     * Unambiguous 32-character set (Base32/Crockford-inspired):
     * Excludes: '0' (zero), 'O' (letter O), '1' (one), 'I' (letter I).
     */
    public static final String UNAMBIGUOUS_ALPHANUMERIC = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    public static final int CODE_LENGTH = 8;
    public static final int DEFAULT_MAX_RETRIES = 5;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a single candidate code of default length (8 chars).
     */
    public String generateCandidate() {
        return generateCandidate(CODE_LENGTH);
    }

    /**
     * Generate a candidate code of specified length.
     */
    public String generateCandidate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(UNAMBIGUOUS_ALPHANUMERIC.charAt(secureRandom.nextInt(UNAMBIGUOUS_ALPHANUMERIC.length())));
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }

    /**
     * Generate a code that is verified to not exist using the provided existence checker predicate.
     * Bounded retry policy (up to maxRetries).
     *
     * @param existsChecker Predicate returning true if the candidate code already exists.
     * @param maxRetries Maximum number of generation attempts before throwing an exception.
     * @return A verified unique code.
     */
    public String generateUniqueCode(Predicate<String> existsChecker, int maxRetries) {
        int attempts = 0;
        while (attempts < maxRetries) {
            attempts++;
            String candidate = generateCandidate(CODE_LENGTH);
            if (existsChecker == null || !existsChecker.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Failed to generate a globally unique pass code after " + maxRetries + " attempts. " +
                "Database collision threshold reached."
        );
    }

    public String generateUniqueCode(Predicate<String> existsChecker) {
        return generateUniqueCode(existsChecker, DEFAULT_MAX_RETRIES);
    }
}
