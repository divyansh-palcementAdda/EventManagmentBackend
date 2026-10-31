package com.app.eventmanagmentsystem.payload.pass;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class BulkActivationPreviewResponse {
    private final String batchId;
    private final UUID eventId;
    private final String eventName;
    private final String eventCode;
    private final String selectionMode;
    private final Long fromSequence;
    private final Long toSequence;
    private final int totalUploadedRows;
    private final int totalTargetPasses;
    private final int eligibleCount;
    private final int conflictActivatedCount;
    private final int conflictRevokedCount;
    private final int conflictAttendeeExistsCount;
    private final int invalidRowCount;
    private final boolean canConfirm;
    private final String rejectionReason;
    private final List<AssignmentPreviewItem> items;
    private final Instant generatedAt;
}

