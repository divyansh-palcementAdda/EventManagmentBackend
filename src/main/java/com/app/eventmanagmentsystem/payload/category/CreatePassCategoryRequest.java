package com.app.eventmanagmentsystem.payload.category;

import com.app.eventmanagmentsystem.model.enums.CategoryScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreatePassCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Category code is required")
    @Size(min = 2, max = 50, message = "Code must be between 2 and 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Code must contain only letters, numbers, underscores, or hyphens")
    private String code;

    @NotNull(message = "Scope is required (GLOBAL or EVENT_SPECIFIC)")
    private CategoryScope scope = CategoryScope.GLOBAL;

    /** Required if scope == EVENT_SPECIFIC. Nullable if scope == GLOBAL. */
    private UUID eventId;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private int displayOrder = 0;
}
