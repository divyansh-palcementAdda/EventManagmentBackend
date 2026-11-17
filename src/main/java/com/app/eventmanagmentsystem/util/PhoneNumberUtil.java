package com.app.eventmanagmentsystem.util;

/**
 * Utility for normalizing and formatting phone numbers consistently across the
 * application.
 */
public final class PhoneNumberUtil {

    private PhoneNumberUtil() {
    }

    /**
     * Checks if a phone number string represents a valid phone number.
     *
     * @param phone Raw phone number string
     * @return true if valid digits between 7 and 15 digits exist, false otherwise
     */
    public static boolean isValid(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.length() >= 7 && digits.length() <= 15;
    }

    /**
     * Normalizes a phone number to canonical E.164-style
     * (+[country_code][national_number]) format.
     *
     * <p>
     * Standardizes representations such as:
     * <ul>
     * <li>+91 9876543210 -> +919876543210</li>
     * <li>+919876543210 -> +919876543210</li>
     * <li>9876543210 -> +919876543210 (10 digits defaults to India country code
     * +91)</li>
     * <li>09876543210 -> +919876543210 (11 digits starting with 0 domestic trunk
     * prefix)</li>
     * <li>+1 (555) 123-4567 -> +15551234567 (International format preserved)</li>
     * </ul>
     *
     * @param phone Raw phone number string in any common format
     * @return Canonical E.164 formatted phone number with leading '+', or null if
     *         input is invalid/empty
     */
    public static String normalize(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.trim();
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.length() < 7) {
            return null;
        }

        // Handle single domestic trunk prefix '0' with 11 digits: 09876543210 ->
        // 9876543210
        if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }

        // 10 digits without country code defaults to India +91
        if (digits.length() == 10) {
            return "+91" + digits;
        }

        // 12 digits starting with 91 (e.g. 919876543210 or +91 9876543210)
        if (digits.length() == 12 && digits.startsWith("91")) {
            return "+" + digits;
        }

        // All other international formats: prepend '+' if not present
        return "+" + digits;
    }

    /**
     * Masks phone number for secure logging and display.
     */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 6)
            return "****";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
    }
}
