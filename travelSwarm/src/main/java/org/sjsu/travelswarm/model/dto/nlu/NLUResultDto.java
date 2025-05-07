package org.sjsu.travelswarm.model.dto.nlu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NLUResultDto {

    private String destination;
    @JsonProperty("duration_days")
    private Integer durationDays;
    private String startDate;
    private String endDate;
    private String budget;
    private List<String> interests;
    private String status;
    @JsonProperty("clarification_question")
    private String clarificationQuestion;
}
