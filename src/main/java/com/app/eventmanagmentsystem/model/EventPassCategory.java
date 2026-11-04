package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.audit.BaseEntity;
import com.app.eventmanagmentsystem.model.enums.ActivationMode;
import com.app.eventmanagmentsystem.model.enums.IdentityPolicy;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "event_pass_categories", indexes = {
        @Index(name = "idx_epc_event", columnList = "event_id"),
        @Index(name = "idx_epc_category", columnList = "category_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_epc_event_category", columnNames = {"event_id", "category_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class EventPassCategory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private PassCategory category;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "activation_mode", nullable = false, length = 30)
    private ActivationMode activationMode = ActivationMode.SELF_SERVICE;

    @Column(name = "qr_required", nullable = false)
    private boolean qrRequired = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_policy", nullable = false, length = 30)
    private IdentityPolicy identityPolicy = IdentityPolicy.COLLECT_IDENTITY;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "pass_limit")
    private Integer passLimit;

    @OneToMany(mappedBy = "eventPassCategory", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<FormFieldConfig> fields = new ArrayList<>();

    @OneToMany(mappedBy = "eventPassCategory", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("versionNumber DESC")
    private List<RegistrationFormVersion> formVersions = new ArrayList<>();

    @Transient
    private RegistrationFormVersion publishedFormVersion;

    public RegistrationFormVersion getPublishedFormVersion() {
        return publishedFormVersion;
    }

    public void setPublishedFormVersion(RegistrationFormVersion version) {
        this.publishedFormVersion = version;
    }

    public PassCategory getPassCategory() {
        return category;
    }

    public void setPassCategory(PassCategory category) {
        this.category = category;
    }

    public boolean isActive() {
        return enabled;
    }

    public void setActive(boolean active) {
        this.enabled = active;
    }
}
