package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Record of pass allotment to an internal representative or department.
 */
@Entity
@Table(name = "pass_allotments", indexes = {
        @Index(name = "idx_pa_event", columnList = "event_id"),
        @Index(name = "idx_pa_allotted_to", columnList = "event_id, allotted_to"),
        @Index(name = "idx_pa_category", columnList = "event_id, category_code")
})
@Getter
@Setter
@NoArgsConstructor
public class PassAllotment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "category_code", nullable = false, length = 50)
    private String categoryCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private PassCategory passCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_category_id")
    private EventPassCategory eventCategory;

    @Column(name = "sequence_identifier", nullable = false, length = 120)
    private String sequenceIdentifier;

    @Column(name = "from_sequence", nullable = false)
    private Long fromSequence;

    @Column(name = "to_sequence", nullable = false)
    private Long toSequence;

    @Column(name = "allotted_to", nullable = false, length = 150)
    private String allottedTo;

    @Column(name = "allotted_quantity", nullable = false)
    private Integer allottedQuantity;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @OneToMany(mappedBy = "allotment", fetch = FetchType.LAZY)
    private List<EventPass> passes = new ArrayList<>();
}
