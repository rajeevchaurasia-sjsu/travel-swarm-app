package org.sjsu.travelswarm.service.client;

import org.sjsu.travelswarm.model.dto.nlu.NLUResultDto;

public interface NLUClient {

    /**
     * Sends user text to the NLU service for parsing.
     * @param userText The raw text input from the user.
     * @return NluResultDto containing parsed parameters, or a fallback DTO if the call fails.
     */
    NLUResultDto parseText(String userText);
}
