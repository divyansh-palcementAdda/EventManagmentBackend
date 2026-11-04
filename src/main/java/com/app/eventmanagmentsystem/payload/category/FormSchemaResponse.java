package com.app.eventmanagmentsystem.payload.category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormSchemaResponse {
    private UUID eventPassCategoryId;
    private UUID versionId;
    private UUID eventId;
    private String eventCode;
    private String eventName;
    private UUID categoryId;
    private String categoryName;
    private String categoryCode;
    private Integer publishedVersionNumber;
    private Integer versionNumber;
    private boolean published;
    private Instant publishedAt;
    private List<FormFieldDto> fields;
    private String schemaSnapshot;

    public Integer getVersionNumber() {
        return versionNumber != null ? versionNumber : publishedVersionNumber;
    }
}
