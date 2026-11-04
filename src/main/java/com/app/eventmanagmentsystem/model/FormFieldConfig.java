package com.app.eventmanagmentsystem.model;

import com.app.eventmanagmentsystem.model.audit.BaseEntity;
import com.app.eventmanagmentsystem.model.enums.FieldType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "form_field_configs", indexes = {
        @Index(name = "idx_ffc_epc", columnList = "event_pass_category_id"),
        @Index(name = "idx_ffc_epc_order", columnList = "event_pass_category_id, display_order")
})
@Getter
@Setter
@NoArgsConstructor
public class FormFieldConfig extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_pass_category_id", nullable = false)
    private EventPassCategory eventPassCategory;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "placeholder", length = 200)
    private String placeholder;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 30)
    private FieldType fieldType = FieldType.TEXT;

    @Column(name = "required", nullable = false)
    private boolean required = false;

    @Column(name = "visible", nullable = false)
    private boolean visible = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "default_value")
    private String defaultValue;

    @Column(name = "validation_rules", columnDefinition = "TEXT")
    private String validationRules;

    @Column(name = "options", columnDefinition = "TEXT")
    private String options;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
