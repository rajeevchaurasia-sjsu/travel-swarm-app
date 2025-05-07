package org.sjsu.travelswarm.model.dto;

import lombok.Data;

@Data
public class ItineraryRequest {
    private String userId;
    private String prompt;
}
