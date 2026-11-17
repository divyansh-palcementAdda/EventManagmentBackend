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
public class PassAllotmentOverallStats {
    private long totalAllotted;
    private long totalActivated;
    private long totalAvailable;
    private long totalCheckedIn;
}
