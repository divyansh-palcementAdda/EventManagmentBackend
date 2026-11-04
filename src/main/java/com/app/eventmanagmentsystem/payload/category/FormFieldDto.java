package com.app.eventmanagmentsystem.payload.category;

import com.app.eventmanagmentsystem.model.enums.FieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormFieldDto {
    private UUID id;

    @NotBlank(message = "Field key is required")
    private String fieldKey;

    @NotBlank(message = "Field label is required")
    private String label;

    private String description;
    private String placeholder;

    @NotNull(message = "Field type is required")
    private FieldType fieldType;

    private boolean required;
    @Builder.Default
    private boolean visible = true;
    private int displayOrder;
    private String defaultValue;
    private String validationRules;
    private String options;
    @Builder.Default
    private boolean active = true;
}
