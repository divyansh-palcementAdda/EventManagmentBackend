package com.app.eventmanagmentsystem.payload.pass;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePassValidityRequest {

    /**
     * Specific pass UUIDs to update (used in individual or multi-select mode).
     */
    private List<UUID> passIds;

    /**
     * Range selection: starting sequence number.
     */
    private Long fromSequence;

    /**
     * Range selection: ending sequence number.
     */
    private Long toSequence;

    /**
     * When true, updates all non-revoked passes for the event.
     */
    private Boolean allEligible;

    /**
     * Permitted event day IDs.
     * If empty or null, the pass is configured as valid for ALL event days.
     */
    private List<UUID> validDayIds;
}

