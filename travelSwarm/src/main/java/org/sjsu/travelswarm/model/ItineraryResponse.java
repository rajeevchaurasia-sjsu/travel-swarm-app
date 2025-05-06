package org.sjsu.travelswarm.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryResponse {
    private String userId;
    private String tripTitle;
    private String city;
    private String startDate;  // Format: YYYY-MM-DD
    private String endDate;    // Format: YYYY-MM-DD
    private List<ItineraryDay> days;
    private List<Stay> stays;
}
