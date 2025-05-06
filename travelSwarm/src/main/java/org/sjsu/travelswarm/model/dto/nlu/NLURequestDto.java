package org.sjsu.travelswarm.model.dto.nlu;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NLURequestDto {
    private String userText;
}
