package org.sjsu.travelswarm.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class PlanningRequestDto {
    private String userId;
    private String destination;
    private Integer durationDays;
    private String startDate;
    private String endDate;
    private String budget;
    private List<String> interests;
    private Map<String, Object> preferences;
}
