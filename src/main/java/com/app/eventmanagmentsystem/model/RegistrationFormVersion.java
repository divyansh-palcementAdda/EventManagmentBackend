package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "registration_form_versions", indexes = {
        @Index(name = "idx_rfv_epc", columnList = "event_pass_category_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_rfv_epc_version", columnNames = {"event_pass_category_id", "version_number"})
})
@Getter
@Setter
@NoArgsConstructor
public class RegistrationFormVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_pass_category_id", nullable = false)
    private EventPassCategory eventPassCategory;

    @Column(name = "version_number", nullable = false)
    private int versionNumber = 1;

    @Column(name = "published", nullable = false)
    private boolean published = true;

    @Lob
    @Column(name = "schema_snapshot", nullable = false, columnDefinition = "LONGTEXT")
    private String schemaSnapshot;
}
