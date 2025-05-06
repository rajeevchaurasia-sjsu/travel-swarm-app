package org.sjsu.travelswarm.dto;

import lombok.Data;

@Data
public class ItineraryRequest {
    private String userId;
    private String prompt;
}
