package org.sjsu.travelswarm.model.dto.nlu;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NLURequestDto {
    private String userText;

    // Add fields to hold the context from the existing PlanningSession
    private String currentDestination;
    private Integer currentDurationDays;
    private String currentStartDate;
    private String currentEndDate;
    private String currentBudget;
    private List<String> currentInterests;
}
