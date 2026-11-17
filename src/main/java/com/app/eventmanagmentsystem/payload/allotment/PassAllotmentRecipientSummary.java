package com.app.eventmanagmentsystem.payload.allotment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassAllotmentRecipientSummary {
    private String allottedTo;
    private long totalAllotted;
    private String categoryDistribution;
    private long totalActivated;
    private long totalAvailable;
    private long totalCheckedIn;
    private List<PassAllotmentCategoryDetail> categories;
}
