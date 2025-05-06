package org.sjsu.travelswarm.service.client;

import org.sjsu.travelswarm.model.dto.nlu.NLUResultDto;
import reactor.core.publisher.Mono;

public interface NLUClient {

    /**
     * Sends user text to the NLU service for parsing.
     * @param userText The raw text input from the user.
     * @return A Mono emitting the NluResultDto containing parsed parameters.
     * Returns a Mono with a fallback DTO if the call fails.
     */
    Mono<NLUResultDto> parseText(String userText);
}
