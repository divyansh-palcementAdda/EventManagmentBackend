package com.app.eventmanagmentsystem.payload.pass;

import com.app.eventmanagmentsystem.model.enums.ActivationMode;
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
public class PassResponse {

    private UUID id;
    private String passNumber;
    private Long sequenceNumber;
    private PassStatus status;
    private UUID eventId;
    private String eventCode;
    private String eventName;

    // Category identity
    private String category;
    private String categoryName;
    private UUID categoryId;
    private UUID eventCategoryId;
    private ActivationMode activationMode;
    private Boolean qrRequired;

    private Instant generatedAt;
    private Instant activatedAt;
    private String attendeeName;
    private String attendeeEmail;
    private String attendeeMobileNumber;
    private boolean checkedIn;
    private Long checkInCount;
    private Instant lastCheckedInAt;
    private List<EventDayResponse> validDays;

    /**
     * Raw QR token — returned ONLY at generation time.
     * Null in all other list/get responses.
     */
    private String rawToken;

    @JsonProperty("category")
    public Object getCategory() {
        if (category == null) return null;
        try {
            return PassCategory.valueOf(category);
        } catch (Exception e) {
            return category;
        }
    }

    public String getCategoryCode() {
        return category;
    }
}
