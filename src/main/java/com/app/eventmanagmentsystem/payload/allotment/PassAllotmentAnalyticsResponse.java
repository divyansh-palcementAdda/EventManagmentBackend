package com.app.eventmanagmentsystem.payload.allotment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassAllotmentAnalyticsResponse {
    private String allottedTo;
    private String categoryCode;
    private String categoryName;
    private PassAllotmentAnalyticsSummary summary;
    private Page<PassAllotmentItemResponse> passes;
}
