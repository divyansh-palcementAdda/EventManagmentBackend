package com.app.eventmanagmentsystem.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Generates the random alphanumeric series code used in pass numbers.
 *
 * <p>Example output: {@code F5EE57F3}
 *
 * <p>The series code is generated once per event and stored in
 * {@link com.app.eventmanagmentsystem.model.Event#passSeriesCode}.
 */
@Component
public class PassSeriesCodeGenerator {

    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int DEFAULT_LENGTH = 8;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        return generate(DEFAULT_LENGTH);
    }

    public String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(secureRandom.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }
}

