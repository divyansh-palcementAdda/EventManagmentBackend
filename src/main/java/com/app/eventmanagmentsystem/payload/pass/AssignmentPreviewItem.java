package com.app.eventmanagmentsystem.payload.pass;

import com.app.eventmanagmentsystem.model.enums.PassStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AssignmentPreviewItem {
    private final int rowNumber;
    private final UUID passId;
    private final String passNumber;
    private final Long sequenceNumber;
    private final PassStatus currentPassStatus;
    private final String existingAttendeeName;

    // Uploaded attendee data
    private final String attendeeFullName;
    private final String attendeeEmail;
    private final String attendeeMobileNumber;
    private final String attendeeOrganization;
    private final String attendeeDesignation;

    // Eligibility calculation
    private final String resultStatus; // ELIGIBLE, CONFLICT_ALREADY_ACTIVATED, CONFLICT_REVOKED, CONFLICT_ATTENDEE_EXISTS, INVALID_DATA
    private final String conflictReason;
}

