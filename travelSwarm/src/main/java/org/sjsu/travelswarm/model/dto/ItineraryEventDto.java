package org.sjsu.travelswarm.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItineraryEventDto {
    private String type;
    private String description;
    private String startTime;
    private String endTime;
    private String details;
}
