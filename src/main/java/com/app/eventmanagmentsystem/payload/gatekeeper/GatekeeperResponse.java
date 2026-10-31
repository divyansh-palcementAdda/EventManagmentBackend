package com.app.eventmanagmentsystem.payload.gatekeeper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatekeeperResponse {

    private UUID assignmentId;
    private UUID eventId;
    private UUID userId;
    private String fullName;
    private String email;
    private String mobileNumber;
    private String accountStatus;
    private boolean active;
    private Instant assignedAt;
    private String assignedByName;
    private boolean alreadyAssigned;
    private List<String> otherAssignedEvents;
}

