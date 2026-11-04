package com.app.eventmanagmentsystem.payload.pass;

import com.app.eventmanagmentsystem.model.enums.PassCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class GeneratePassRequest {

    @NotNull(message = "Event ID is required")
    private UUID eventId;

    @Min(value = 1, message = "Batch size must be at least 1")
    @Max(value = 500, message = "Batch size cannot exceed 500")
    private int batchSize = 1;

    /** Legacy enum category: STUDENT or OTHER. */
    private PassCategory category = PassCategory.STUDENT;

    /** Dynamic category code: e.g. "STUDENT", "OTHER", "VIP", "FACULTY", "SPONSOR", etc. */
    private String categoryCode;

    /** Optional reference to configured EventPassCategory. */
    private UUID eventCategoryId;

    /** Optional client-supplied idempotency key (for batch-of-1 requests). */
    private String idempotencyKey;

    /** Optional specific event days for pass validity. If empty/null, valid for all days of the event. */
    private List<UUID> validDayIds;

    public String resolveCategoryCode() {
        if (categoryCode != null && !categoryCode.isBlank()) {
            return categoryCode.trim().toUpperCase();
        }
        if (category != null) {
            return category.name();
        }
        return "STUDENT";
    }
}
