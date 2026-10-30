package com.app.eventmanagmentsystem.model.enums;

public enum ActivationPolicy {
    /** Anyone with the pass link/token can self-activate. */
    SELF_SERVICE,
    /** An admin must manually activate each pass. */
    ADMIN_ONLY,
    /** Activation window governed by activationStartsAt / activationEndsAt. */
    SCHEDULED
}

