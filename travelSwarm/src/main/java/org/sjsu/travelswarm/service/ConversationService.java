package org.sjsu.travelswarm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.sjsu.travelswarm.model.dto.FinalItineraryDto;
import org.sjsu.travelswarm.model.dto.ItineraryDayDto;
import org.sjsu.travelswarm.model.dto.ItineraryEventDto;
import org.sjsu.travelswarm.model.dto.PlanningRequestDto;
import org.sjsu.travelswarm.model.dto.nlu.NLUResultDto;
import org.sjsu.travelswarm.model.entity.Itinerary;
import org.sjsu.travelswarm.model.entity.PlanningSession;
import org.sjsu.travelswarm.model.enums.SessionStatus;
import org.sjsu.travelswarm.repository.PlanningSessionRepository;
import org.sjsu.travelswarm.service.client.NLUClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class ConversationService {

    private final NLUClient nluClient;
    private final PlanningRequestPublisher planningRequestPublisher;
    private final ObjectMapper objectMapper;
    private final PlanningSessionRepository planningSessionRepository;
    private final ItineraryService itineraryService;
    private final TelegramBotService telegramBotService;

    @Autowired
    public ConversationService(NLUClient nluClient,
                               PlanningRequestPublisher planningRequestPublisher,
                               ObjectMapper objectMapper,
                               PlanningSessionRepository planningSessionRepository,
                               ItineraryService itineraryService,
                               @Lazy TelegramBotService telegramBotService) {
        this.nluClient = nluClient;
        this.planningRequestPublisher = planningRequestPublisher;
        this.objectMapper = objectMapper;
        this.planningSessionRepository = planningSessionRepository;
        this.itineraryService = itineraryService;
        this.telegramBotService = telegramBotService;
    }

    /**
     * Main entry point to process text messages from the user (called by Telegram Bot).
     */
    @Transactional
    public void processTelegramUpdate(long chatId, String userText) {
        try {
            log.info("Processing message from chatId {}: '{}'", chatId, userText);

            // Find the latest active session for this user, or create a new one
            List<SessionStatus> activeStatuses = List.of(SessionStatus.STARTED, SessionStatus.WAITING_FOR_CLARIFICATION);
            PlanningSession session = planningSessionRepository
                    .findFirstByChatIdAndStatusInOrderByUpdatedAtDesc(chatId, activeStatuses)
                    .orElseGet(() -> {
                        log.info("No active session found for chatId {}. Creating new session.", chatId);
                        return new PlanningSession(chatId); // Creates session with STARTED status
                    });

            NLUResultDto nluResult = nluClient.parseText(userText);
            handleNluResult(session, nluResult);
        } catch (Exception e) {
            log.error("Error during NLU call or initial handling for chatId {}: {}", chatId, e.getMessage(), e);
            handleError(chatId, "NLU processing or initial handling failed", e);
        }
    }

    /**
     * Handles the result received from the NLU service.
     * Now operates within a transaction to ensure session state is saved consistently.
     */
    @Transactional // Make DB operations atomic within this method
    protected void handleNluResult(PlanningSession session, NLUResultDto nluResult) {
        long chatId = session.getChatId();
        log.info("Handling NLU result for chatId {}: Status='{}', Dest='{}'",
                chatId, nluResult.getStatus(), nluResult.getDestination());

        // Update session with NLU results (even if partial)
        session.setDestination(nluResult.getDestination() != null ? nluResult.getDestination() : session.getDestination());
        session.setDurationDays(nluResult.getDurationDays() != null ? nluResult.getDurationDays() : session.getDurationDays());
        session.setStartDate(nluResult.getStartDate() != null ? nluResult.getStartDate() : session.getStartDate());
        session.setEndDate(nluResult.getEndDate() != null ? nluResult.getEndDate() : session.getEndDate());
        session.setBudget(nluResult.getBudget() != null ? nluResult.getBudget() : session.getBudget());
        session.setInterests(nluResult.getInterests() != null ? nluResult.getInterests() : session.getInterests());
        // TODO: Persist preferences map if NLU extracts it
        // session.setPreferences(nluResult.getPreferences() != null ? nluResult.getPreferences() : session.getPreferences());

        if ("NEEDS_CLARIFICATION".equals(nluResult.getStatus())) {
            log.info("NLU requires clarification for chatId {}. Question: {}", chatId, nluResult.getClarificationQuestion());
            session.setStatus(SessionStatus.WAITING_FOR_CLARIFICATION);
            session.setLastClarificationQuestion(nluResult.getClarificationQuestion());
            planningSessionRepository.save(session); // Save updated state
            sendTelegramResponse(chatId, nluResult.getClarificationQuestion());

        } else if ("COMPLETE".equals(nluResult.getStatus()) && session.getDestination() != null && (session.getDurationDays() != null || (session.getStartDate() != null && session.getEndDate() != null))) {
            log.info("NLU parsing complete for chatId {}. Preparing planning request.", chatId);
            // Ensure all necessary fields are populated on the session object now
            session.setStatus(SessionStatus.PROCESSING); // Mark as request sent
            session.setLastClarificationQuestion(null); // Clear clarification question

            String correlationId = UUID.randomUUID().toString();
            session.setCorrelationId(correlationId); // Store correlation ID in DB

            // Save the session BEFORE publishing the message
            planningSessionRepository.save(session);
            log.info("Saved session for Correlation ID {} / Chat ID {}", correlationId, chatId);

            // Build the DTO from the session state
            PlanningRequestDto planningRequest = PlanningRequestDto.builder()
                    .userId(String.valueOf(chatId))
                    .destination(session.getDestination())
                    .durationDays(session.getDurationDays())
                    .startDate(session.getStartDate())
                    .endDate(session.getEndDate())
                    .budget(session.getBudget())
                    .interests(session.getInterests())
                    .preferences(session.getPreferences()) // Pass preferences if stored
                    .build();

            // Publish the request to RabbitMQ
            planningRequestPublisher.publishRequest(planningRequest, correlationId);

            sendTelegramResponse(chatId, "Okay, planning your trip to " + planningRequest.getDestination() + "... I'll send the itinerary when it's ready!");

        } else {
            log.warn("NLU status ('{}') not 'COMPLETE' or mandatory info still missing for chatId {}", nluResult.getStatus(), chatId);
            session.setStatus(SessionStatus.WAITING_FOR_CLARIFICATION);
            String clarification = (nluResult.getClarificationQuestion() != null && !nluResult.getClarificationQuestion().isBlank())
                    ? nluResult.getClarificationQuestion()
                    : "I'm missing some key details (like destination or duration). Could you please provide them?";
            session.setLastClarificationQuestion(clarification);
            planningSessionRepository.save(session);
            sendTelegramResponse(chatId, clarification);
        }
    }

    /**
     * Handles the final itinerary result received from the results queue.
     */
    @Transactional
    public void handlePlanningResult(String correlationId, FinalItineraryDto itineraryDto) {
        log.info("Received final itinerary for Correlation ID: {}", correlationId);

        Optional<PlanningSession> sessionOpt = planningSessionRepository.findByCorrelationId(correlationId);

        if (sessionOpt.isPresent()) {
            PlanningSession session = sessionOpt.get();
            long chatId = session.getChatId();
            log.info("ConversationService: Found PlanningSession ID {} for Chat ID {} (CorrID: {})",
                    session.getId(), chatId, correlationId);

            boolean processingError = false;
            // Check if DTO indicates an error from Python side (e.g. if it's a raw string with "error")
            if (itineraryDto.getDestination() == null && (itineraryDto.getDays() == null || itineraryDto.getDays().isEmpty())) {
                if (itineraryDto.getSummary() != null && itineraryDto.getSummary().toLowerCase().contains("error")) {
                    processingError = true;
                } else if (itineraryDto.getGeneral_notes() != null && itineraryDto.getGeneral_notes().stream().anyMatch(s -> s.toLowerCase().contains("error"))) {
                    processingError = true;
                } else {
                    // If critical fields are missing, assume it's an error structure rather than a valid itinerary
                    log.warn("Itinerary DTO seems to be an error fallback for CorrID {}. DTO: {}", correlationId, itineraryDto);
                    processingError = true; // Treat as error if key fields missing
                }
            }

            if (processingError) {
                session.setStatus(SessionStatus.FAILED);
                log.error("Itinerary generation reported failure for Correlation ID {}. DTO: {}", correlationId, itineraryDto);
                sendTelegramResponse(chatId, "Sorry, I encountered an error while generating the details of your itinerary. Please try again.");
            } else {
                try {
                    Itinerary savedItinerary = itineraryService.storeItinerary(itineraryDto, String.valueOf(chatId));
                    log.info("Itinerary DTO stored successfully with DB ID: {}", savedItinerary.getId());
                    session.setFinalItineraryId(savedItinerary.getId());
                    session.setStatus(SessionStatus.COMPLETED);
                    log.info("Planning session COMPLETED for Correlation ID {}", correlationId);

                    String formattedItinerary = formatItineraryForTelegram(itineraryDto);
                    sendTelegramResponse(chatId, formattedItinerary);
                } catch (Exception e) {
                    log.error("Failed to store or send itinerary for Correlation ID: {}. Error: {}", correlationId, e.getMessage(), e);
                    session.setStatus(SessionStatus.FAILED);
                    sendTelegramResponse(chatId, "I found an itinerary, but there was an issue processing or saving it. Please try again.");
                }
            }
            planningSessionRepository.save(session);

        } else {
            log.warn("Received itinerary result for unknown or already processed Correlation ID: {}. Ignoring.", correlationId);
        }
    }

    private void sendTelegramResponse(long chatId, String text) {
        telegramBotService.sendTextMessage(chatId, text);
    }

    public void handleError(long chatId, String contextMessage, Throwable error) {
        log.error("Handling error for chatId {}: Context='{}', Error='{}'", chatId, contextMessage, error.getMessage(), error);
        // Attempt to find an active session to mark as FAILED, if appropriate
        List<SessionStatus> activeStatuses = List.of(SessionStatus.STARTED, SessionStatus.WAITING_FOR_CLARIFICATION, SessionStatus.PROCESSING);
        planningSessionRepository.findFirstByChatIdAndStatusInOrderByUpdatedAtDesc(chatId, activeStatuses)
                .ifPresent(session -> {
                    session.setStatus(SessionStatus.FAILED);
                    // Avoid overwriting a specific NLU clarification question with a generic error
                    if (session.getLastClarificationQuestion() == null || !session.getLastClarificationQuestion().contains("issue understanding that")) {
                        session.setLastClarificationQuestion("Error: " + contextMessage);
                    }
                    planningSessionRepository.save(session);
                });
        sendTelegramResponse(chatId, "Sorry, something went wrong while processing your request \\(`" + contextMessage + "`\\)\\. Please try again later\\.");
    }

    private String formatItineraryForTelegram(FinalItineraryDto dto) {
        log.info("Formatting FinalItineraryDto for Telegram output for destination: {}", dto.getDestination());
        StringBuilder sb = new StringBuilder();
        sb.append("*Trip to ").append(dto.getDestination()).append("*\n\n");
        if (dto.getSummary() != null && !dto.getSummary().isBlank()) {
            sb.append("_").append(dto.getSummary()).append("_\n\n");
        }
        sb.append("*Duration:* ").append(dto.getDurationDays() != null ? dto.getDurationDays() + " days" : "Not specified").append("\n");
        if (dto.getStartDate() != null) sb.append("*Start Date:* ").append(dto.getStartDate()).append("\n");
        if (dto.getEndDate() != null) sb.append("*End Date:* ").append(dto.getEndDate()).append("\n");
        if (dto.getBudget() != null) sb.append("*Budget:* ").append(dto.getBudget()).append("\n");
        if (dto.getInterests() != null && !dto.getInterests().isEmpty()) {
            sb.append("*Interests:* ").append(String.join(", ", dto.getInterests())).append("\n");
        }
        sb.append("\n");

        if (dto.getDays() != null) {
            for (ItineraryDayDto day : dto.getDays()) {
                sb.append("*Day ").append(day.getDay()).append(":* ");
                if (day.getTheme() != null) sb.append("_").append(day.getTheme()).append("_");
                sb.append("\n");
                if (day.getEvents() != null) {
                    for (ItineraryEventDto event : day.getEvents()) {
                        sb.append("  • *").append(event.getType() != null ? event.getType().toUpperCase() : "EVENT").append(":* ");
                        sb.append(event.getDescription());
                        if (event.getStartTime() != null) sb.append(" \\(Around: ").append(event.getStartTime()).append("\\)");
                        if (event.getDetails() != null && !event.getDetails().isBlank()) {
                            sb.append("\n    _Details:_ ").append(event.getDetails().replace("\n", "\n    "));
                        }
                        sb.append("\n");
                    }
                }
                sb.append("\n");
            }
        }
        if (dto.getGeneral_notes() != null && !dto.getGeneral_notes().isEmpty()) {
            sb.append("*General Notes:*\n");
            for (String note : dto.getGeneral_notes()) {
                sb.append("• ").append(note).append("\n");
            }
        }

        return sb.toString();
    }
}