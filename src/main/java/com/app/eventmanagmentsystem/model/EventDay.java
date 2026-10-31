package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Represents an individual day of a single-day or multi-day event.
 * Used for dynamic pass validity and day-specific check-ins.
 */
@Entity
@Table(name = "event_days", indexes = {
        @Index(name = "idx_ed_event_day", columnList = "event_id, day_number", unique = true),
        @Index(name = "idx_ed_event_date", columnList = "event_id, event_date", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@Builder
public class EventDay extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Event event;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    public EventDay(Event event, int dayNumber, LocalDate eventDate, String label) {
        this.event = event;
        this.dayNumber = dayNumber;
        this.eventDate = eventDate;
        this.label = label;
    }
}

