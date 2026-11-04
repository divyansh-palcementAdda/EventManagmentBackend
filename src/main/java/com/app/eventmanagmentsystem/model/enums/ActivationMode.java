package com.app.eventmanagmentsystem.model.enums;

/**
 * Defines the activation workflow for an event-category combination.
 */
public enum ActivationMode {
    /** QR scan displays dynamic registration form for attendee to fill and activate. */
    SELF_SERVICE,

    /** Immediate atomic pass activation on QR scan without form when NO_IDENTITY is configured. */
    SCAN_AUTO_ACTIVATE,

    /** On-site staff-only admission without QR code generation or public registration. */
    NO_QR_WALK_IN
}
