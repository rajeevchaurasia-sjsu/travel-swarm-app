package org.sjsu.travelswarm.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    private String location;
    private String cost;
    private String bookingInfo;
    private String travelTime;
    private String distance;
    private String transportMode;
    private String website;
    private String notes;
    @JsonProperty("opening_hours")
    private String openingHours;
}
