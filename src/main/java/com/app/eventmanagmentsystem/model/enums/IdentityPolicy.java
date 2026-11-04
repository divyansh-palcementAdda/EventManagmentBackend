package com.app.eventmanagmentsystem.model.enums;

/**
 * Defines whether personal attendee identity must be collected during activation or admission.
 */
public enum IdentityPolicy {
    /** Collects configured attendee details (e.g. name, email, mobile, custom fields). */
    COLLECT_IDENTITY,

    /** Activates or admits without collecting personal attendee details or creating dummy profiles. */
    NO_IDENTITY
}
