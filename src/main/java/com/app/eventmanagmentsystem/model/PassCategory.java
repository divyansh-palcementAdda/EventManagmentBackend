package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.audit.BaseEntity;
import com.app.eventmanagmentsystem.model.enums.CategoryScope;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pass_categories", indexes = {
        @Index(name = "idx_pc_scope", columnList = "scope"),
        @Index(name = "idx_pc_event", columnList = "event_id"),
        @Index(name = "idx_pc_code", columnList = "code")
})
@Getter
@Setter
@NoArgsConstructor
public class PassCategory extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 30)
    private CategoryScope scope = CategoryScope.GLOBAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
