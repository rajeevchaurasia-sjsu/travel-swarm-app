package org.sjsu.travelswarm.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class PlanningRequestDto implements Serializable {
    private String userId;
    private String destination;
    @JsonProperty("duration_days")
    private Integer durationDays;
    private String startDate;
    private String endDate;
    private String budget;
    private List<String> interests;
    private Map<String, Object> preferences;
}
