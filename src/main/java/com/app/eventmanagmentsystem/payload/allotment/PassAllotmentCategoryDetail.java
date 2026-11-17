package com.app.eventmanagmentsystem.payload.allotment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassAllotmentCategoryDetail {
    private String categoryCode;
    private String categoryName;
    private long allotted;
    private long activated;
    private long available;
    private long checkedIn;
    private List<UUID> allotmentIds;
}
