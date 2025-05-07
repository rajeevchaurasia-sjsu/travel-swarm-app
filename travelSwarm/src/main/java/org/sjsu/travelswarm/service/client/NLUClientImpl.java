package org.sjsu.travelswarm.service.client;

import lombok.extern.slf4j.Slf4j;
import org.sjsu.travelswarm.model.dto.nlu.NLURequestDto;
import org.sjsu.travelswarm.model.dto.nlu.NLUResultDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;

@Service
@Slf4j
public class NLUClientImpl implements NLUClient {

    private final WebClient webClient;

    @Value("${agent.service.nlu.url}")
    private String nluServiceUrl;

    @Autowired
    public NLUClientImpl(WebClient.Builder webClientBuilder) {
        // Configure WebClient instance
        log.info("Initializing NLU WebClient for base URL: {}", nluServiceUrl);
        this.webClient = webClientBuilder
                .baseUrl(nluServiceUrl) // Set base URL for the Python service
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("NLU WebClient initialized for base URL: {}", nluServiceUrl);
    }

    @Override
    public Mono<NLUResultDto> parseText(String userText) {
        if (userText == null || userText.isBlank()) {
            log.warn("parseText called with empty userText.");
            return Mono.just(createFallbackNluResult("Empty input received."));
        }

        NLURequestDto requestDto = new NLURequestDto(userText);
        log.info("Sending request to NLU service: {}", requestDto);

        return webClient.post() // HTTP POST method
                // .uri() // No extra path needed if base URL includes '/parse_request'
                // If base URL is just http://agent_service:5001, use .uri("/parse_request")
                .bodyValue(requestDto) // Set the request body (automatically converted to JSON)
                .retrieve() // Execute the request and retrieve the response
                .bodyToMono(NLUResultDto.class) // Decode the response body to NluResultDto
                .timeout(Duration.ofSeconds(20)) // Add a timeout for the call
                .doOnSuccess(response -> log.info("Received NLU Result: Status='{}', Dest='{}'", response.getStatus(), response.getDestination()))
                .doOnError(error -> log.error("NLU request failed: {}", error.getMessage(), error))
                .onErrorResume(error -> {
                    log.warn("Returning fallback NLU result due to error: {}", error.getMessage());
                    // Return a default DTO wrapped in Mono if any error occurs (timeout, connection refused, 5xx etc.)
                    return Mono.just(createFallbackNluResult(error.getMessage()));
                });
    }

    /**
     * Helper method to create a default/fallback NLU Result DTO.
     * Used when the NLU call fails or input is invalid.
     */
    private NLUResultDto createFallbackNluResult(String errorDetails) {
        NLUResultDto fallback = new NLUResultDto();
        fallback.setStatus("NEEDS_CLARIFICATION"); // Default to needing clarification
        fallback.setClarificationQuestion("Sorry, I encountered an issue understanding that (" + errorDetails + "). Could you please rephrase your request clearly?");
        // Set other fields to null or empty lists
        fallback.setInterests(Collections.emptyList());
        return fallback;
    }
}
