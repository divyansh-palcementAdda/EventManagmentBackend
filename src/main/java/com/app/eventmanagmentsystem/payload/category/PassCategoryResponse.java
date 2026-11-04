package com.app.eventmanagmentsystem.payload.category;

import com.app.eventmanagmentsystem.model.enums.CategoryScope;
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
public class PassCategoryResponse {
    private UUID id;
    private String name;
    private String code;
    private CategoryScope scope;
    private UUID eventId;
    private String eventName;
    private String description;
    private boolean active;
    private int displayOrder;
    private long totalPasses;
    private long activeEventsCount;
    private Instant createdAt;
    private Instant updatedAt;
}
