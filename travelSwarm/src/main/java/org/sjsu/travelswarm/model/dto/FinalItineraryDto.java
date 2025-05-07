package org.sjsu.travelswarm.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinalItineraryDto {
    private String destination;
    @JsonProperty(value = "duration_days")
    private Integer durationDays;
    @JsonProperty(value = "start_date")
    private String startDate;
    @JsonProperty(value = "end_date")
    private String endDate;
    private String budget;
    private List<String> interests;
    private String summary;
    private List<ItineraryDayDto> days;
    private Double estimatedTotalCost;
    private List<String> general_notes;
}