package org.sjsu.travelswarm.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItineraryDayDto {

    private int day;
    private String date;
    private String theme;
    private List<ItineraryEventDto> events;
}
