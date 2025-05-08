package org.sjsu.travelswarm.service;

import lombok.extern.slf4j.Slf4j;
import org.sjsu.travelswarm.model.dto.FinalItineraryDto;
import org.sjsu.travelswarm.model.dto.ItineraryDayDto;
import org.sjsu.travelswarm.model.dto.ItineraryEventDto;
import org.sjsu.travelswarm.model.dto.PlanningRequestDto;
import org.sjsu.travelswarm.model.dto.nlu.NLUResultDto;
import org.sjsu.travelswarm.model.entity.Itinerary;
import org.sjsu.travelswarm.model.entity.PlanningSession;
import org.sjsu.travelswarm.model.enums.SessionStatus;
import org.sjsu.travelswarm.repository.ItineraryRepository;
import org.sjsu.travelswarm.repository.PlanningSessionRepository;
import org.sjsu.travelswarm.service.client.NLUClient;
import org.sjsu.travelswarm.util.MarkdownUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class ConversationService {

    private final NLUClient nluClient;
    private final PlanningRequestPublisher planningRequestPublisher;
    private final PlanningSessionRepository planningSessionRepository;
    private final ItineraryService itineraryService;
    private final TelegramBotService telegramBotService;
    private final ItineraryRepository itineraryRepository;

    @Autowired
    public ConversationService(NLUClient nluClient,
                               PlanningRequestPublisher planningRequestPublisher,
                               PlanningSessionRepository planningSessionRepository,
                               ItineraryService itineraryService,
                               ItineraryRepository itineraryRepository,
                               @Lazy TelegramBotService telegramBotService) {
        this.nluClient = nluClient;
        this.planningRequestPublisher = planningRequestPublisher;
        this.planningSessionRepository = planningSessionRepository;
        this.itineraryService = itineraryService;
        this.telegramBotService = telegramBotService;
        this.itineraryRepository = itineraryRepository;
    }

    /**
     * Main entry point to process text messages from the user (called by Telegram Bot).
     */
    @Transactional
    public void processTelegramUpdate(Long chatId, String message) {
        try {
            log.info("Processing message from chatId {}: '{}'", chatId, message);

            Optional<PlanningSession> sessionOpt = planningSessionRepository.findByChatId(chatId);

            PlanningSession session;
            if (sessionOpt.isPresent()) {
                session = sessionOpt.get();
                log.info("Found existing session ID {} for chatId {} with status {}", session.getId(), chatId, session.getStatus());

                // If it's already processing, tell user and exit
                if (session.getStatus() == SessionStatus.PROCESSING) {
                    telegramBotService.sendTextMessage(chatId,
                            "⏳ *I'm still working on your previous request\\.* Please wait for the itinerary to be ready before sending new messages\\."); // Pre-escaped
                    return;
                }

                // If it was COMPLETED or FAILED, reset it for a new conversation flow initiated by a non-command message.
                // (Commands like /new handle their own reset logic within handleCommand)
                // We only reset here if the user sends a regular message, implying they want to start over implicitly.
                if (!message.startsWith("/") && (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.FAILED)) {
                    log.info("Resetting completed/failed session ID {} for chatId {} due to new user message.", session.getId(), chatId);
                    session.setStatus(SessionStatus.STARTED);
                    // Clear previous planning data
                    session.setDestination(null);
                    session.setDurationDays(null);
                    session.setStartDate(null);
                    session.setEndDate(null);
                    session.setBudget(null);
                    session.setInterests(null);
                    session.setPreferences(null);
                    session.setLastClarificationQuestion(null);
                    session.setCorrelationId(null); // Clear correlation ID until a new request is published
                    session.setFinalItineraryId(null);
                    // Note: @PreUpdate in PlanningSession entity will handle updatedAt automatically on save
                }
                // If STARTED or WAITING_FOR_CLARIFICATION, just continue using it.
                // updatedAt will be handled by @PreUpdate upon saving changes later in the flow.
            } else {
                // No session exists for this chatId, create a new one
                log.info("No existing session found for chatId {}. Creating a new one.", chatId);
                session = new PlanningSession();
                session.setChatId(chatId);
                session.setStatus(SessionStatus.STARTED);
                // @PrePersist in PlanningSession entity will handle createdAt and updatedAt
                // No need to set correlationId yet

                // Save immediately to get the ID and persist the new session
                session = planningSessionRepository.save(session); // This is now safe as no prior session exists
                log.info("Created and saved new session ID {} for chatId {}", session.getId(), chatId);
            }

            // --- IMPORTANT ---
            // The original code saved the session *within* the `if (existingSession.isPresent())` block
            // and also when creating a new one. The new logic saves when creating.
            // We also need to ensure any modifications (like resetting, or later updates in handlers)
            // are saved before the transaction commits. Saving explicitly here ensures the reset state persists.
            // Or rely on subsequent saves within handleNluResult/handleCommand.
            // Let's ensure the reset state is saved before proceeding:
            if (sessionOpt.isPresent() && !message.startsWith("/") && (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.FAILED)) {
                session = planningSessionRepository.save(session); // Save the reset state explicitly
            }
            // --- End modification ---

            // Process the message using the obtained/created/reset session
            // Note: The 'session' object might have been modified (reset) or is the newly created one.
            if (message.startsWith("/")) {
                handleCommand(chatId, message, session);
            } else {
                handleUserMessage(chatId, message, session);
            }
        } catch (Exception e) {
            log.error("Error during message processing for chatId {}: {}", chatId, e.getMessage(), e);
            handleError(chatId, e, "Message processing failed");
        }
    }

    private void handleCommand(Long chatId, String command, PlanningSession currentSession) {
        String commandBase = command.split(" ")[0].toLowerCase();
        String[] commandArgs = command.split(" ");

        switch (command.toLowerCase()) {
            case "/start":
                telegramBotService.sendTextMessage(chatId,
                        "🌟 *Welcome to TravelSwarm\\!* 🌟\n\n" + // Keep your \\!
                                "I'm your personal travel planning assistant\\! I can help you create amazing travel experiences\\. Here's what I can do:\n\n" +
                                "📝 *Available Commands:*\n" +
                                "• /new \\- Start planning a new adventure\n" +
                                "• /history \\- View your past itineraries\n" +
                                "• /help \\- Show this guide\n\n" +
                                "🎯 *To plan your perfect trip, just tell me:*\n" +
                                "• Where you want to go 🌍\n" +
                                "• When you want to go 📅\n" +
                                "• Your interests and preferences 🎨\n" +
                                "• Your budget 💰\n\n" +
                                "You can either type /new to start planning, or simply tell me where you'd like to go\\! For example: \"I want to visit Paris for 3 days\" or \"Plan a trip to Tokyo\"\\!"
                );
                break;
            case "/new":
                // Use the session object passed into handleCommand.
                // The processTelegramUpdate method ensures 'currentSession' is the correct, unique session for this chatId.
                // We just need to reset its state for the new request.
                log.info("Handling /new command for chatId {}. Resetting session ID {}.", chatId, currentSession.getId());
                currentSession.setStatus(SessionStatus.STARTED);
                // Clear previous planning data
                currentSession.setDestination(null);
                currentSession.setDurationDays(null);
                currentSession.setStartDate(null);
                currentSession.setEndDate(null);
                currentSession.setBudget(null);
                currentSession.setInterests(null);
                currentSession.setPreferences(null);
                currentSession.setLastClarificationQuestion(null);
                currentSession.setCorrelationId(null); // Clear correlation ID
                currentSession.setFinalItineraryId(null);
                // @PreUpdate in PlanningSession entity handles updatedAt

                planningSessionRepository.save(currentSession); // Save the reset state

                // Send response (ensure it's escaped using the util)
                telegramBotService.sendTextMessage(chatId, MarkdownUtil.escapeMarkdownV2("🎒 *Let's plan your next adventure!* Where would you like to explore?"));
                break;
            case "/history":
                log.info("Handling /history command for chatId {}", chatId);
                List<Itinerary> itineraries = itineraryService.getItinerariesForUser(chatId);

                if (itineraries.isEmpty()) {
                    telegramBotService.sendTextMessage(chatId, MarkdownUtil.escapeMarkdownV2("You don't have any saved itineraries yet\\. Use /new to create one\\!")); // Escaped . !
                } else {
                    StringBuilder historyMsg = new StringBuilder("*Your Past Itineraries:*\n\n");
                    int count = 1;
                    for (Itinerary itinerary : itineraries) {
                        String title = MarkdownUtil.escapeMarkdownV2(itinerary.getTripTitle() != null ? itinerary.getTripTitle() : "Trip to " + itinerary.getCity());
                        String dateStr = itinerary.getStartDate() != null ?
                                itinerary.getStartDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "No Start Date";
                        String escapedDateInfo = MarkdownUtil.escapeMarkdownV2(dateStr); // <<< --- ESCAPE THE DATE STRING HERE

                        historyMsg.append(count).append("\\. ").append(title)
                                .append(" \\(ID: `").append(itinerary.getId()).append("`, Start: ")
                                .append(escapedDateInfo) // <<< --- APPEND THE ESCAPED DATE
                                .append("\\)\n");
                        count++;
                        if (count > 10) {
                            historyMsg.append("\\.\\.\\. (showing latest 10)\n");
                            break;
                        }
                    }
                    log.info("history message till now :: {}", historyMsg);
                    historyMsg.append("\nType `/view <ID>` to see the details of an itinerary\\.");
                    telegramBotService.sendTextMessage(chatId, historyMsg.toString());
                }
                break; // End of history case

            case "/view": // <<< --- ADD THIS CASE (for viewing details) ---
                log.info("Handling /view command for chatId {}", chatId);
                if (commandArgs.length > 1) {
                    try {
                        Long itineraryIdToView = Long.parseLong(commandArgs[1]);
                        viewSpecificItinerary(chatId, itineraryIdToView);
                    } catch (NumberFormatException e) {
                        telegramBotService.sendTextMessage(chatId, MarkdownUtil.escapeMarkdownV2("Invalid ID format. Please use `/view <ID>` where <ID> is a number from your /history list."));
                    }
                } else {
                    telegramBotService.sendTextMessage(chatId, MarkdownUtil.escapeMarkdownV2("Please provide the ID of the itinerary you want to view. Usage: `/view <ID>`"));
                }
                break; // <<< --- END OF VIEW CASE ---
            case "/help":
                String helpMsg = "🌟 *TravelSwarm Help* 🌟\n\n" +
                        "Here's how to use me:\n\n" +
                        "1\\. *Start Planning:*\n" +
                        "   • Type /new or just tell me where you want to go\\.\n" +
                        "   • Example: \"I want to visit Paris for 3 days\"\n\n" +
                        "2\\. *Modify a Trip:*\n" +
                        "   • Type /modify to change your last completed trip\\.\n" +
                        "   • Or say \"modify my trip\" or \"change my itinerary\"\\.\n\n" +
                        "3\\. *View Past Trips:*\n" +
                        "   • Type /history to list your saved itineraries\\.\n" +
                        "   • Type /view \\<ID\\> to see details for a specific ID from the list\\.\n\n" +
                        "4\\. *During Planning:*\n" +
                        "   • Answer my questions about your preferences\\.\n" +
                        "   • I'll help you create the perfect itinerary\\!\n\n" +
                        "Need more help? Just ask\\!";
                telegramBotService.sendTextMessage(chatId, helpMsg);
                break;
            default:
                telegramBotService.sendTextMessage(chatId, 
                    "🤷‍♂️ *I don't understand that command.* Type /help to see what I can do!"
                );
        }
    }

    private void viewSpecificItinerary(Long chatId, Long itineraryId) {
        log.info("Attempting to view itinerary ID {} for user {}", itineraryId, chatId);
        Optional<Itinerary> itineraryOpt = itineraryRepository.findById(itineraryId);
        String userId = String.valueOf(chatId);

        itineraryOpt.ifPresentOrElse(itinerary -> {
            // Security check: Ensure the itinerary belongs to the requesting user
            if (!itinerary.getUserId().equals(userId)) {
                log.warn("User {} attempted to view itinerary ID {} which belongs to another user.", userId, itineraryId);
                telegramBotService.sendTextMessage(chatId, MarkdownUtil.escapeMarkdownV2("Sorry, you can only view your own itineraries."));
                return;
            }

            log.info("Found itinerary ID {}. Converting to DTO and formatting.", itineraryId);
            try {
                // --- Conversion step needed ---
                FinalItineraryDto dto = itineraryService.convertEntityToDto(itinerary); // We need to implement this method
                String formattedItinerary = formatItineraryForTelegram(dto);
                sendTelegramResponse(chatId, formattedItinerary);
            } catch (Exception e) {
                log.error("Error converting or formatting itinerary ID {} for viewing: {}", itineraryId, e.getMessage(), e);
                sendTelegramResponse(chatId, MarkdownUtil.escapeMarkdownV2("Sorry, there was an error retrieving the details for that itinerary\\."));
            }

        }, () -> {
            // Itinerary ID not found
            log.warn("Itinerary ID {} not found for viewing request by user {}", itineraryId, userId);
            sendTelegramResponse(chatId, MarkdownUtil.escapeMarkdownV2("Sorry, I couldn't find an itinerary with ID `" + itineraryId + "`\\. Use /history to see available IDs\\."));
        });
    }

    private void handleUserMessage(Long chatId, String message, PlanningSession session) {
        try {
            // Process the message with NLU
            NLUResultDto nluResult = nluClient.parseText(message, session);

            handleNluResult(session, nluResult);
        } catch (Exception e) {
            log.error("Error during NLU processing for chatId {}: {}", chatId, e.getMessage(), e);
            handleError(chatId, e, "NLU processing failed");
        }
    }

    /**
     * Handles the result received from the NLU service.
     * Now operates within a transaction to ensure session state is saved consistently.
     */
    @Transactional
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

            String escapedDest = MarkdownUtil.escapeMarkdownV2(planningRequest.getDestination() != null ? planningRequest.getDestination() : "your destination");
            // Manually escape the dots and exclamation mark HERE
            String confirmationMsg = "Okay, planning your trip to *" + escapedDest + "*\\.\\.\\. I'll send the itinerary when it's ready\\!";
            sendTelegramResponse(chatId, confirmationMsg);
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
                sendTelegramResponse(chatId, "Sorry, I encountered an error while generating the details of your itinerary\\. Please try again\\.");
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
                    sendTelegramResponse(chatId, "I found an itinerary, but there was an issue processing or saving it\\. Please try again\\.");
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleError(long chatId, Throwable error, String contextMessage) {
        log.error("Handling error for chatId {}: Context='{}', Error='{}'", chatId, contextMessage, error.getMessage(), error);
        
        try {
            // Attempt to find an active session to mark as FAILED
        List<SessionStatus> activeStatuses = List.of(SessionStatus.STARTED, SessionStatus.WAITING_FOR_CLARIFICATION, SessionStatus.PROCESSING);
        planningSessionRepository.findFirstByChatIdAndStatusInOrderByUpdatedAtDesc(chatId, activeStatuses)
                .ifPresent(session -> {
                    session.setStatus(SessionStatus.FAILED);
                        session.setLastClarificationQuestion("Error: " + contextMessage);
                    planningSessionRepository.save(session);
                });

            // Send user-friendly error message
            String errorMessage = "😅 *Oops!* Something went wrong while processing your request.\n\n" +
                                "Please try again or use /new to start a fresh planning session.";
            telegramBotService.sendTextMessage(chatId, errorMessage);
        } catch (Exception e) {
            log.error("Error while handling error for chatId {}: {}", chatId, e.getMessage(), e);
            // Last resort error message
            try {
                telegramBotService.sendTextMessage(chatId, "😅 Oops! Something went wrong. Please try again later.");
            } catch (Exception ex) {
                log.error("Failed to send error message to chatId {}: {}", chatId, ex.getMessage(), ex);
            }
        }
    }

    private String formatItineraryForTelegram(FinalItineraryDto dto) {
        log.info("Formatting FinalItineraryDto for Telegram output for destination: {}",
                dto.getDestination() != null ? MarkdownUtil.escapeMarkdownV2(dto.getDestination()) : "N/A"); // Log escaped
        StringBuilder sb = new StringBuilder();

        // Trip header
        sb.append("✈️ *Trip to ").append(MarkdownUtil.escapeMarkdownV2(dto.getDestination() != null ? dto.getDestination() : "N/A")).append("*\n\n");

        // Summary
        if (dto.getSummary() != null && !dto.getSummary().isBlank()) {
            sb.append("_").append(MarkdownUtil.escapeMarkdownV2(dto.getSummary())).append("_\n\n");
        }

        // Trip details
        sb.append("⏱ *Duration:* ").append(MarkdownUtil.escapeMarkdownV2(dto.getDurationDays() != null ? dto.getDurationDays() + " days" : "Not specified")).append("\n");
        if (dto.getStartDate() != null) sb.append("📅 *Start Date:* ").append(MarkdownUtil.escapeMarkdownV2(dto.getStartDate().toString())).append("\n"); // Assuming LocalDate or similar
        if (dto.getEndDate() != null) sb.append("📅 *End Date:* ").append(MarkdownUtil.escapeMarkdownV2(dto.getEndDate().toString())).append("\n");
        if (dto.getBudget() != null) sb.append("💰 *Budget:* ").append(MarkdownUtil.escapeMarkdownV2(dto.getBudget())).append("\n");
        if (dto.getInterests() != null && !dto.getInterests().isEmpty()) {
            // Escape each interest before joining
            List<String> escapedInterests = dto.getInterests().stream().map(MarkdownUtil::escapeMarkdownV2).toList();
            sb.append("🎯 *Interests:* ").append(String.join(", ", escapedInterests)).append("\n");
        }
        if (dto.getEstimatedTotalCost() != null) {
            sb.append("💵 *Estimated Total Cost:* $").append(MarkdownUtil.escapeMarkdownV2(dto.getEstimatedTotalCost().toString())).append("\n");
        }
        sb.append("\n");
        sb.append("\n");

        // Daily itinerary
        if (dto.getDays() != null) {
            for (ItineraryDayDto day : dto.getDays()) {
                sb.append("\n");
                sb.append("📅 *Day ").append(MarkdownUtil.escapeMarkdownV2(String.valueOf(day.getDay()))).append(":* "); // day.getDay() is likely int/String
                if (day.getTheme() != null) sb.append("_").append(MarkdownUtil.escapeMarkdownV2(day.getTheme())).append("_");
                sb.append("\n");

                if (day.getEvents() != null) {
                    for (ItineraryEventDto event : day.getEvents()) {
                        sb.append("\n");
                        String eventEmoji = getEventEmoji(event.getType());
                        sb.append("  ").append(eventEmoji).append(" *").append(MarkdownUtil.escapeMarkdownV2(event.getType() != null ? event.getType().toUpperCase() : "EVENT")).append(":* ");
                        sb.append(MarkdownUtil.escapeMarkdownV2(event.getDescription()));

                        if (event.getStartTime() != null) {
                            sb.append("\n    🕒 *Time:* ").append(MarkdownUtil.escapeMarkdownV2(event.getStartTime()));
                            if (event.getEndTime() != null) {
                                sb.append(" \\- ").append(MarkdownUtil.escapeMarkdownV2(event.getEndTime())); // Escape the hyphen if it's literal content, or use \n if it's structure
                            }
                        }

                        if (event.getLocation() != null) {
                            sb.append("\n    📍 *Location:* ").append(MarkdownUtil.escapeMarkdownV2(event.getLocation()));
                        }

                        if (event.getCost() != null) {
                            sb.append("\n    💰 *Cost:* ").append(MarkdownUtil.escapeMarkdownV2(event.getCost().toString())); // Assuming cost might be a number
                        }

                        if (event.getBookingInfo() != null) {
                            sb.append("\n    🎫 *Booking:* ").append(MarkdownUtil.escapeMarkdownV2(event.getBookingInfo()));
                        }

                        if ("transport".equalsIgnoreCase(event.getType()) || "transportation".equalsIgnoreCase(event.getType())) {
                            if (event.getTravelTime() != null) {
                                sb.append("\n    ⏱ *Travel Time:* ").append(MarkdownUtil.escapeMarkdownV2(event.getTravelTime()));
                            }
                            if (event.getDistance() != null) {
                                sb.append("\n    📏 *Distance:* ").append(MarkdownUtil.escapeMarkdownV2(event.getDistance()));
                            }
                            if (event.getTransportMode() != null) {
                                sb.append("\n    🚌 *Mode:* ").append(MarkdownUtil.escapeMarkdownV2(event.getTransportMode()));
                            }
                        }

                        if (StringUtils.hasText(event.getOpeningHours())) {
                            sb.append("\n    ⏰ *Hours:* ").append(MarkdownUtil.escapeMarkdownV2(event.getOpeningHours()));
                        }
                        if (StringUtils.hasText(event.getWebsite())) {
                            // Make it clickable if it's a valid URL (basic check)
                            String url = event.getWebsite();
                            String escapedUrl = MarkdownUtil.escapeMarkdownV2(url); // Escape URL content for display
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                // Ensure internal parentheses/special chars in URL are handled if needed for MarkdownV2 links
                                // Basic link format: [display text](url) - URL needs escaping for MarkdownV2 link syntax '()'
                                String linkUrl = url.replace(")", "\\)").replace("(", "\\("); // Basic escaping for link context
                                sb.append("\n    🌐 *Website:* [Link](").append(linkUrl).append(")");
                            } else {
                                // Display as text if not a clear URL
                                sb.append("\n    🌐 *Website:* ").append(escapedUrl);
                            }
                        }

                        if (event.getDetails() != null && !event.getDetails().isBlank()) {
                            // Escape then replace newlines for indented Markdown
                            String escapedDetails = MarkdownUtil.escapeMarkdownV2(event.getDetails());
                            sb.append("\n    ℹ️ *Details:* ").append(escapedDetails.replace("\n", "\n    "));
                        }
                        if (event.getNotes() != null && !event.getNotes().isBlank()) {
                            String escapedNotes = MarkdownUtil.escapeMarkdownV2(event.getNotes());
                            sb.append("\n    📝 *Notes:* ").append(escapedNotes.replace("\n", "\n    "));
                        }
                        sb.append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        if (dto.getGeneral_notes() != null && !dto.getGeneral_notes().isEmpty()) {
            sb.append("\n");
            sb.append("📝 *General Notes:*\n");
            for (String note : dto.getGeneral_notes()) {
                sb.append("• ").append(MarkdownUtil.escapeMarkdownV2(note)).append("\n"); // Escape each note
            }
        }
        return sb.toString();
    }

    private String getEventEmoji(String eventType) {
        if (eventType == null) return "•";
        return switch (eventType.toLowerCase()) {
            case "attraction", "museum" -> "🏛";
            case "food", "meal" -> "🍽";
            case "accommodation", "stay" -> "🏨";
            case "transportation", "transport" -> "🚗";
            case "activity" -> "🎯";
            case "shopping" -> "🛍";
            case "entertainment" -> "🎭";
            case "nature", "park" -> "🌳";
            case "beach" -> "🏖";
            case "nightlife" -> "🌃";
            default -> "•";
        };
    }
}