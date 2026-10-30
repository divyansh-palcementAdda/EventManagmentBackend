package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Atomic permission stored in the database.
 * Named by convention: RESOURCE_ACTION (e.g. PASS_GENERATE, EVENT_CREATE).
 */
@Entity
@Table(name = "permissions", indexes = {
        @Index(name = "idx_permissions_name", columnList = "name", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class Permission extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 80)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "resource", length = 60)
    private String resource;

    @Column(name = "action", length = 60)
    private String action;

    public Permission(String name, String description, String resource, String action) {
        this.name = name;
        this.description = description;
        this.resource = resource;
        this.action = action;
    }
}

