package com.app.eventmanagmentsystem.util;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Derives an event code from an event name.
 *
 * <p>Algorithm: take the first uppercase letter of each word and join them.
 * Example: "Renaissance Coffee Festival" → "RCF"
 *
 * <p>Administrators may override the generated code before publication.
 * Uniqueness is enforced by the database.
 */
@Component
public class EventCodeGenerator {

    private static final Pattern WORD_SPLITTER = Pattern.compile("\\s+");
    private static final Pattern VALID_CODE = Pattern.compile("^[A-Z0-9]{2,20}$");

    public String generateFromName(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("Event name must not be blank");
        }
        String code = Arrays.stream(WORD_SPLITTER.split(eventName.trim()))
                .filter(w -> !w.isBlank())
                .map(w -> String.valueOf(w.toUpperCase(Locale.ROOT).charAt(0)))
                .collect(Collectors.joining());

        // If only one word, take first 3 chars
        if (code.length() == 1) {
            code = eventName.replaceAll("[^A-Za-z0-9]", "")
                    .toUpperCase(Locale.ROOT)
                    .substring(0, Math.min(3, eventName.replaceAll("[^A-Za-z0-9]", "").length()));
        }
        return code.toUpperCase(Locale.ROOT);
    }

    public boolean isValidCode(String code) {
        return code != null && VALID_CODE.matcher(code).matches();
    }
}

