package com.app.eventmanagmentsystem.payload.category;

import com.app.eventmanagmentsystem.model.enums.ActivationMode;
import com.app.eventmanagmentsystem.model.enums.IdentityPolicy;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateEventCategoryPolicyRequest {

    private Boolean enabled;

    @NotNull(message = "Activation mode is required")
    private ActivationMode activationMode;

    private Boolean qrRequired;

    @NotNull(message = "Identity policy is required")
    private IdentityPolicy identityPolicy;

    private Integer displayOrder;

    private Integer passLimit;
}
