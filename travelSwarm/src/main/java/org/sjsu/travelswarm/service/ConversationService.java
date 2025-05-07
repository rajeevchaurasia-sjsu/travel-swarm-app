package org.sjsu.travelswarm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sjsu.travelswarm.model.dto.FinalItineraryDto;
import org.sjsu.travelswarm.model.dto.PlanningRequestDto;
import org.sjsu.travelswarm.model.dto.nlu.NLUResultDto;
import org.sjsu.travelswarm.model.entity.PlanningSession;
import org.sjsu.travelswarm.model.enums.SessionStatus;
import org.sjsu.travelswarm.repository.PlanningSessionRepository;
import org.sjsu.travelswarm.service.client.NLUClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationService {

    private final NLUClient nluClient;
    private final PlanningRequestPublisher planningRequestPublisher;
    private final ObjectMapper objectMapper;
    private final PlanningSessionRepository planningSessionRepository;

    // TODO: Inject ItineraryStorageService for saving final itineraries
    // TODO: Inject TelegramMessageService for sending replies


    /**
     * Main entry point to process text messages from the user (called by Telegram Bot).
     */
    public void processTelegramUpdate(long chatId, String userText) {
        try {
            log.info("Processing message from chatId {}: '{}'", chatId, userText);

            // Find the latest active session for this user, or create a new one
            List<SessionStatus> activeStatuses = List.of(SessionStatus.STARTED, SessionStatus.WAITING_FOR_CLARIFICATION);
            // Using findFirst... handles cases where multiple rapid messages might create race conditions if not careful
            PlanningSession session = planningSessionRepository
                    .findFirstByChatIdAndStatusInOrderByUpdatedAtDesc(chatId, activeStatuses)
                    .orElseGet(() -> {
                        log.info("No active session found for chatId {}. Creating new session.", chatId);
                        return new PlanningSession(chatId); // Creates session with STARTED status
                    });

            // Combine context if needed (advanced) - for now, just use current text
            // if (session.getStatus() == SessionStatus.WAITING_FOR_CLARIFICATION) { ... }

            // Call NLU service asynchronously
            nluClient.parseText(userText)
                    .subscribe(nluResult -> handleNluResult(session, nluResult), // Pass session object
                            error -> handleError(session.getChatId(), "NLU processing failed", error)
                    );
        } catch (Exception e) {
            log.error("Error processing Telegram update for chatId {}: {}", chatId, e.getMessage(), e);
            handleError(chatId, "General error in processing", e);
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
            // Handle cases where NLU returns COMPLETE but mandatory info still missing (shouldn't happen with good NLU prompt)
            // Or handle other unexpected statuses
            log.warn("NLU status is COMPLETE but mandatory info might be missing, or status is unexpected ('{}') for chatId {}", nluResult.getStatus(), chatId);
            session.setStatus(SessionStatus.WAITING_FOR_CLARIFICATION); // Revert to clarification
            session.setLastClarificationQuestion("Sorry, I still need a bit more information. Please provide the destination and duration/dates.");
            planningSessionRepository.save(session);
            sendTelegramResponse(chatId, session.getLastClarificationQuestion());
        }
    }

    /**
     * Handles the final itinerary result received from the results queue.
     * (This method will be called by PlanningResultListener).
     */
    @Transactional // Make DB operations atomic
    public void handlePlanningResult(String correlationId, FinalItineraryDto itineraryDto) {
        log.info("Received final itinerary for Correlation ID: {}", correlationId);

        // Find the session using the correlation ID
        Optional<PlanningSession> sessionOpt = planningSessionRepository.findByCorrelationId(correlationId);

        if (sessionOpt.isPresent()) {
            PlanningSession session = sessionOpt.get();
            long chatId = session.getChatId();
            log.info("Found matching PlanningSession with ID {} for Chat ID {}", session.getId(), chatId);

            // Check if result indicates an error from the agent service
            boolean processingError = itineraryDto.getDays() == null && itineraryDto.getSummary() == null && itineraryDto.getGeneral_notes().stream().anyMatch(s -> s.contains("error")); // Basic error check

            if (processingError) {
                session.setStatus(SessionStatus.FAILED);
                log.error("Itinerary generation failed for Correlation ID {}. Error details might be in DTO: {}", correlationId, itineraryDto);
                // TODO: Extract specific error from DTO if Python service sends it structured
                sendTelegramResponse(chatId, "Sorry, I encountered an error while generating the itinerary. Please try again later.");
            } else {
                // --- TODO: Save the received itinerary ---
                // 1. Create/Inject ItineraryStorageService
                // 2. Call itineraryStorageService.saveItinerary(itineraryDto, String.valueOf(chatId));
                // 3. Get the saved Itinerary entity's ID
                Long savedItineraryId = null; // Placeholder = itineraryStorageService.saveItinerary(...);
                log.warn("TODO: Implement ItineraryStorageService to save the received itinerary DTO. Placeholder ID: {}", savedItineraryId);
                session.setFinalItineraryId(savedItineraryId); // Link session to saved itinerary
                // ----------------------------------------

                session.setStatus(SessionStatus.COMPLETED); // Mark session as completed
                log.info("Planning session completed successfully for Correlation ID {}", correlationId);

                // --- TODO: Format and Send Itinerary ---
                String formattedItinerary = formatItineraryForTelegram(itineraryDto);
                sendTelegramResponse(chatId, formattedItinerary);
                // --------------------------------------
            }
            // Save the final status of the session
            planningSessionRepository.save(session);

        } else {
            log.warn("Received itinerary result for unknown or already processed Correlation ID: {}. Ignoring.", correlationId);
        }
    }

    // ...(Keep sendTelegramResponse, handleError, formatItineraryForTelegram placeholders)...
    private void sendTelegramResponse(long chatId, String text) {
        log.warn("TODO: Implement sending message via Telegram Bot - ChatID: {}, Message: '{}'", chatId, text);
    }
    private void handleError(long chatId, String context, Throwable error) {
        log.error("Error during async processing for chatId {}: Context='{}', Error='{}'", chatId, context, error.getMessage(), error);
        sendTelegramResponse(chatId, "Sorry, something went wrong while processing your request. Please try again later.");
    }
    private String formatItineraryForTelegram(FinalItineraryDto dto) {
        log.warn("TODO: Implement proper formatting for FinalItineraryDto");
        try {
            return "Your itinerary is ready!\n```json\n" +
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(dto) +
                    "\n```";
        } catch (JsonProcessingException e) { return "Error formatting itinerary."; }
    }
}