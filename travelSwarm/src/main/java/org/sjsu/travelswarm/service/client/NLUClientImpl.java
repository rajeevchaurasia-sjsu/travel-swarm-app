package org.sjsu.travelswarm.service.client;

import lombok.extern.slf4j.Slf4j;
import org.sjsu.travelswarm.model.dto.nlu.NLURequestDto;
import org.sjsu.travelswarm.model.dto.nlu.NLUResultDto;
import org.sjsu.travelswarm.model.entity.PlanningSession;
import org.sjsu.travelswarm.model.enums.SessionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;

@Service
@Slf4j
public class NLUClientImpl implements NLUClient {

    private final RestTemplate restTemplate;
    private final String nluServiceFullUrl;

    @Autowired
    public NLUClientImpl(RestTemplate restTemplate, @Value("${agent.service.nlu.url}") String injectedNluServiceBaseUrl) {
        this.restTemplate = restTemplate;

        if (injectedNluServiceBaseUrl == null || injectedNluServiceBaseUrl.isBlank()) {
            log.error("NLUClientImpl Constructor: NLU Service Base URL IS NULL OR BLANK. Check 'agent.service.nlu.url' property.");
            this.nluServiceFullUrl = null;
        } else {
            this.nluServiceFullUrl = injectedNluServiceBaseUrl.trim() + "/parse_request";
            log.info("NLUClientImpl Constructor: Full NLU Service URL configured to: '{}'", this.nluServiceFullUrl);
        }
    }

    @PostConstruct
    public void postConstructCheck() {
        log.info("NLUClientImpl @PostConstruct - Effective nluServiceFullUrl stored: '{}'", this.nluServiceFullUrl);
    }

    @Override
    public NLUResultDto parseText(String userText, PlanningSession currentSession) {
        if (this.nluServiceFullUrl == null || this.nluServiceFullUrl.isBlank()) {
            log.error("NLUClientImpl.parseText - Aborting call: NLU Service Full URL was not configured properly at startup.");
            return createFallbackNluResult("NLU service URL not configured. Critical error.");
        }

        if (userText == null || userText.isBlank()) {
            log.warn("parseText called with empty userText.");
            return createFallbackNluResult("Empty input received.");
        }

        log.info("NLUClientImpl.parseText - Preparing NLU request for text: '{}' with context from session ID: {}",
                userText, currentSession != null ? currentSession.getId() : "null");

        // Create the request DTO, populating context from the session
        NLURequestDto requestDto = new NLURequestDto();
        requestDto.setUserText(userText);

        if (currentSession != null) {
            requestDto.setCurrentDestination(currentSession.getDestination());
            requestDto.setCurrentDurationDays(currentSession.getDurationDays());
            requestDto.setCurrentStartDate(currentSession.getStartDate());
            requestDto.setCurrentEndDate(currentSession.getEndDate());
            requestDto.setCurrentBudget(currentSession.getBudget());
            requestDto.setCurrentInterests(currentSession.getInterests());
            requestDto.setCurrentStatus(currentSession.getStatus().name());
        } else {
            requestDto.setCurrentStatus(SessionStatus.STARTED.name());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<NLURequestDto> requestEntity = new HttpEntity<>(requestDto, headers);

        log.info("NLUClientImpl.parseText - Attempting POST to Full URL: [{}]. Payload: {}",
                this.nluServiceFullUrl, requestDto);

        try {
            URI serviceUri = new URI(this.nluServiceFullUrl);
            ResponseEntity<NLUResultDto> responseEntity = restTemplate.postForEntity(
                    serviceUri,
                    requestEntity,
                    NLUResultDto.class);

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                NLUResultDto nluResult = responseEntity.getBody();
                log.info("Received NLU Result (RestTemplate): Status='{}', Dest='{}'", nluResult.getStatus(), nluResult.getDestination());
                return nluResult;
            } else {
                log.error("NLU request (RestTemplate) to {} returned status: {} with body: {}",
                        this.nluServiceFullUrl, responseEntity.getStatusCode(), responseEntity.getBody());
                return createFallbackNluResult("NLU service error: " + responseEntity.getStatusCode());
            }
        } catch (URISyntaxException e) {
            log.error("Invalid URI syntax for NLU service URL: {}. Error: {}", this.nluServiceFullUrl, e.getMessage(), e);
            return createFallbackNluResult("Invalid NLU service URL configured: " + e.getMessage());
        } catch (RestClientException e) {
            log.error("NLU request (RestTemplate) to {} FAILED: {}", this.nluServiceFullUrl, e.getMessage(), e);
            return createFallbackNluResult(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during NLU call (RestTemplate) to {}: {}", this.nluServiceFullUrl, e.getMessage(), e);
            return createFallbackNluResult("Unexpected error: " + e.getMessage());
        }
    }

    private NLUResultDto createFallbackNluResult(String errorDetails) {
        // ... (keep this method as before) ...
        NLUResultDto fallback = new NLUResultDto();
        fallback.setStatus("NEEDS_CLARIFICATION");
        fallback.setClarificationQuestion("Sorry, I encountered an issue understanding that (" + errorDetails + "). Could you please rephrase your request clearly?");
        fallback.setInterests(Collections.emptyList());
        fallback.setDestination(null);
        fallback.setDurationDays(null);
        fallback.setStartDate(null);
        fallback.setEndDate(null);
        fallback.setBudget(null);
        return fallback;
    }
}