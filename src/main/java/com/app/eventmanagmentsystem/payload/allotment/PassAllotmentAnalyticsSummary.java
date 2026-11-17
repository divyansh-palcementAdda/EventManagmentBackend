package com.app.eventmanagmentsystem.payload.allotment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassAllotmentAnalyticsSummary {
    private long totalAllotted;
    private long activated;
    private long available;
    private long checkedIn;
    private long activatedNotCheckedIn;
}
