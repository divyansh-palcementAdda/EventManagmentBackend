package com.app.eventmanagmentsystem.payload.pass;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Detached, immutable snapshot containing all data required to render
 * a pass image or fallback badge image independently of an open Hibernate session.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassImageSnapshot {
    private UUID passId;
    private String passNumber;
    private Long sequenceNumber;
    private String category;
    private boolean noQr;
    private String attendeeName;
    private String eventName;
    private String eventVenue;
    private String timezone;
    private Instant eventStartsAt;
    private Instant eventEndsAt;
    private List<LocalDate> validDates;

    public List<LocalDate> getValidDates() {
        return validDates != null ? Collections.unmodifiableList(validDates) : Collections.emptyList();
    }

    public boolean isNoQr() {
        return noQr;
    }
}
