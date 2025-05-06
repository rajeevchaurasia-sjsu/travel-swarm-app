package org.sjsu.travelswarm.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Service
@Slf4j
public class TelegramBotService extends TelegramLongPollingBot {

    private final String botUsername;
    private final ConversationService conversationService;

    @Autowired
    public TelegramBotService(@Value("${telegram.bot.token}") String botToken,
                                @Value("${telegram.bot.username}") String botUsername,
                                @Lazy ConversationService conversationService) {
        super(botToken);
        this.botUsername = botUsername;
        this.conversationService = conversationService;
        log.info("TelegramBotComponent initialized with username: {}", this.botUsername);
    }


    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String userText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();

            log.info("Received message from chatId {}: '{}'", chatId, userText);

            if ("/start".equals(userText)) {
                sendTextMessage(chatId, "Welcome to TravelSwarm! Tell me about your desired trip: destination, duration/dates, interests, and budget.");
                return;
            }

            try {
                conversationService.processTelegramUpdate(chatId, userText);
            } catch (Exception e) {
                log.error("Error processing update for chatId {}: {}", chatId, e.getMessage(), e);
                sendTextMessage(chatId, "Sorry, an error occurred while processing your request. Please try again.");
            }
        }
    }

    @Override
    public String getBotUsername() {
        return this.botUsername;
    }

    /**
     * Public method to send a text message back to a specific chat.
     * This will be called by ConversationService to send replies.
     */
    public void sendTextMessage(long chatId, String text) {
        if (text == null || text.isBlank()) {
            log.warn("Attempted to send null or blank message to chatId {}", chatId);
            return;
        }
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        try {
            log.info("Sending message to chatId {}: '{}'", chatId, text.substring(0, Math.min(text.length(), 100)) + "...");
            execute(sendMessage);
            log.debug("Message sent successfully to chatId {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chatId {}: {}", chatId, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending message to chatId {}: {}", chatId, e.getMessage(), e);
        }
    }

    // Register the bot with TelegramBotsApi
    // This is needed when NOT using SpringLongPollingBot's auto-registration
    @PostConstruct
    public void registerBot() {
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(this);
            log.info("TelegramBotComponent registered successfully!");
        } catch (TelegramApiException e) {
            log.error("Error registering TelegramBotComponent: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void cleanUp() {
        log.info("TelegramBotComponent shutting down.");
    }
}
