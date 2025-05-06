package org.sjsu.travelswarm.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinalItineraryDto {
    private String destination;
    private Integer durationDays;
    private String startDate;
    private String endDate;
    private String budget;
    private List<String> interests;
    private String summary;
    private List<ItineraryDayDto> days;
    private Double estimatedTotalCost;
    private List<String> general_notes;
}