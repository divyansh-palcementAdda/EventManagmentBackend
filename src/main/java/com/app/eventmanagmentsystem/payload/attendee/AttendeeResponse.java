package com.app.eventmanagmentsystem.payload.attendee;

import com.app.eventmanagmentsystem.model.enums.PassCategory;
import com.app.eventmanagmentsystem.model.enums.RegistrationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class AttendeeResponse {

    private UUID id;
    private UUID eventId;
    private String eventCode;
    private String eventName;
    private String fullName;
    private String email;
    private String mobileNumber;
    private String organization;
    private String designation;
    private String college;
    private String course;
    private String yearSemester;
    private String studentId;
    private RegistrationStatus registrationStatus;
    private Instant registeredAt;

    // Associated pass details if claimed/assigned
    private boolean hasPass;
    private UUID passId;
    private String passNumber;
    private Long sequenceNumber;
    private String passStatus;
    private PassCategory passCategory;

    // Check-in details
    private boolean checkedIn;
    private String checkInStatus;
    private int checkInCount;
    private Instant lastCheckedInAt;
    private Instant checkedInAt;

    // Nested structures for rich client consumption
    private EventSummary event;
    private LinkedPassSummary linkedPass;
    private List<CheckInSummary> checkInHistory;

    private Instant createdAt;
    private Instant updatedAt;

    @JsonProperty("hasPass")
    public boolean isHasPass() {
        return hasPass || passId != null || passNumber != null || linkedPass != null;
    }

    @JsonProperty("category")
    public PassCategory getCategory() {
        return passCategory;
    }

    @JsonProperty("pass")
    public LinkedPassSummary getPass() {
        return linkedPass;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventSummary {
        private UUID id;
        private String eventCode;
        private String name;
        private Instant startsAt;
        private Instant endsAt;
        private String venue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkedPassSummary {
        private UUID id;
        private String passNumber;
        private Long sequenceNumber;
        private String status;
        private PassCategory category;
        private String token;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckInSummary {
        private UUID id;
        private String gateName;
        private String operatorName;
        private Instant checkedInAt;
        private Integer dayNumber;
    }
}
