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
public class PassSequenceOptionResponse {
    private String sequenceId;
    private String displayName;
    private String categoryCode;
    private String categoryName;
    private Long fromSequence;
    private Long toSequence;
    private long totalPasses;
    private long allottedPasses;
    private long availablePasses;
}
