package com.app.eventmanagmentsystem.payload.category;

import com.app.eventmanagmentsystem.model.enums.ActivationMode;
import com.app.eventmanagmentsystem.model.enums.IdentityPolicy;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ConfigureEventCategoryRequest {

    @NotNull(message = "Category ID is required")
    private UUID categoryId;

    private boolean enabled = true;

    @NotNull(message = "Activation mode is required")
    private ActivationMode activationMode = ActivationMode.SELF_SERVICE;

    private Boolean qrRequired;

    @NotNull(message = "Identity policy is required")
    private IdentityPolicy identityPolicy = IdentityPolicy.COLLECT_IDENTITY;

    private int displayOrder = 0;

    private Integer passLimit;
}
