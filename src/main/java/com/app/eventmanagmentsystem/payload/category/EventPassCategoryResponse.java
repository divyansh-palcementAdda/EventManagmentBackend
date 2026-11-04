package com.app.eventmanagmentsystem.payload.category;

import com.app.eventmanagmentsystem.model.enums.ActivationMode;
import com.app.eventmanagmentsystem.model.enums.CategoryScope;
import com.app.eventmanagmentsystem.model.enums.IdentityPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventPassCategoryResponse {
    private UUID id;
    private UUID eventId;
    private String eventCode;
    private String eventName;
    private UUID categoryId;
    private String categoryName;
    private String categoryCode;
    private CategoryScope categoryScope;
    private boolean enabled;
    private ActivationMode activationMode;
    private boolean qrRequired;
    private IdentityPolicy identityPolicy;
    private int displayOrder;
    private Integer passLimit;
    private Integer currentPassCount;
    private Integer publishedVersionNumber;
    private Instant createdAt;
    private Instant updatedAt;
}
