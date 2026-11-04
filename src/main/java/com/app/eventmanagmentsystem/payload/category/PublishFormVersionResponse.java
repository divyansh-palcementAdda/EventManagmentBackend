package com.app.eventmanagmentsystem.payload.category;

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
public class PublishFormVersionResponse {
    private UUID versionId;
    private UUID eventPassCategoryId;
    private int versionNumber;
    private boolean published;
    private int fieldCount;
    private String changelog;
    private Instant publishedAt;
    private Instant createdAt;

    public Instant getPublishedAt() {
        return publishedAt != null ? publishedAt : createdAt;
    }
}
