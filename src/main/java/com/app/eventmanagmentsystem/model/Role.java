package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Application role (SUPER_ADMIN, EVENT_ADMIN, GATEKEEPER, VIEWER).
 * Stored in the database for dynamic management.
 */
@Entity
@Table(name = "roles", indexes = {
        @Index(name = "idx_roles_name", columnList = "name", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "permissions")
public class Role extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 60)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    /** True means the role was seeded by the system and cannot be deleted. */
    @Column(name = "system_defined", nullable = false)
    private boolean systemDefined = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    public Role(String name, String description, boolean systemDefined) {
        this.name = name;
        this.description = description;
        this.systemDefined = systemDefined;
    }
}

