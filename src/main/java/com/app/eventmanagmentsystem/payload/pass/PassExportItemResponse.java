package com.app.eventmanagmentsystem.payload.pass;

import com.app.eventmanagmentsystem.model.enums.PassCategory;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.event.EventDayResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassExportItemResponse {
    private UUID passId;
    private String passNumber;
    private Long sequenceNumber;
    private String category;
    private String categoryName;
    private PassStatus status;
    private UUID eventId;
    private String eventName;
    private String eventCode;
    private String venue;
    private Instant startsAt;
    private Instant endsAt;
    private List<EventDayResponse> validDays;
    private String attendeeName;
    private String attendeeEmail;
    private String attendeeMobileNumber;
    private String qrData;

    @JsonProperty("category")
    public Object getCategory() {
        if (category == null) return null;
        try {
            return PassCategory.valueOf(category);
        } catch (Exception e) {
            return category;
        }
    }
}
