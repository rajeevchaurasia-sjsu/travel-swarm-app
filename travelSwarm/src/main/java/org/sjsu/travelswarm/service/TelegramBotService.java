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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.sjsu.travelswarm.util.MarkdownUtil.escapeMarkdownV2;

@Service
@Slf4j
public class TelegramBotService extends TelegramLongPollingBot {

    private final String botUsername;
    private final ConversationService conversationService;

    private static final int MAX_MESSAGE_LENGTH = 4000; // Using 4000 for MarkdownV2
    private static final Pattern MARKDOWN_PATTERN = Pattern.compile("[_*\\[\\]()~`>#+\\-=|{}.!]");

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
                sendTextMessage(chatId,
                        "🌟 *Welcome to TravelSwarm\\!* 🌟\n\n" + // Keep your \\!
                                "I'm your personal travel planning assistant\\! I can help you create amazing travel experiences\\. Here's what I can do:\n\n" +
                                "📝 *Available Commands:*\n" +
                                "• /new \\- Start planning a new adventure\n" +
                                "• /history - View your past itineraries\n" +
                                "• /help \\- Show this guide\n\n" +
                                "🎯 *To plan your perfect trip, just tell me:*\n" +
                                "• Where you want to go 🌍\n" +
                                "• When you want to go 📅\n" +
                                "• Your interests and preferences 🎨\n" +
                                "• Your budget 💰\n\n" +
                                "You can either type /new to start planning, or simply tell me where you'd like to go\\! For example: \"I want to visit Paris for 3 days\" or \"Plan a trip to Tokyo\"\\!"
                );
                return;
            }

            try {
                conversationService.processTelegramUpdate(chatId, userText);
            } catch (Exception e) {
                log.error("Error processing update for chatId {}: {}", chatId, e.getMessage(), e);
                sendTextMessage(chatId, "😅 Oops\\! Something went wrong while processing your request\\. Please try again\\!");
            }
        }
    }

    @Override
    public String getBotUsername() {
        return this.botUsername;
    }

    public void sendTextMessage(Long chatId, String text) {
        try {
            // Split message if it's too long
            if (text.length() > MAX_MESSAGE_LENGTH) {
                List<String> parts = splitMessage(text);
                for (String part : parts) {
                    try {
                        executeSendMessage(chatId, part);
                    } catch (TelegramApiException e) {
                        log.error("Error sending message part to chat {}: {}", chatId, e.getMessage(), e);
                        sendFallbackMessage(chatId);
                    }
                }
            } else {
                try {
                    executeSendMessage(chatId, text);
                } catch (TelegramApiException e) {
                    log.error("Error sending message to chat {}: {}", chatId, e.getMessage(), e);
                    sendFallbackMessage(chatId);
                }
            }
        } catch (Exception e) {
            log.error("Error processing message for chat {}: {}", chatId, e.getMessage(), e);
            sendFallbackMessage(chatId);
        }
    }

    private void executeSendMessage(Long chatId, String text) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.enableMarkdownV2(true);
        message.setParseMode("MarkdownV2");
        execute(message);
    }

    private List<String> splitMessage(String text) {
        List<String> parts = new ArrayList<>();
        int maxLength = MAX_MESSAGE_LENGTH;
        
        // Split by double newlines to preserve formatting
        String[] sections = text.split("\\n\\n");
        StringBuilder currentPart = new StringBuilder();
        
        for (String section : sections) {
            if (currentPart.length() + section.length() + 2 > maxLength) {
                if (currentPart.length() > 0) {
                    parts.add(currentPart.toString());
                    currentPart = new StringBuilder();
                }
                // If a single section is too long, split it by single newlines
                if (section.length() > maxLength) {
                    String[] lines = section.split("\\n");
                    StringBuilder currentLine = new StringBuilder();
                    for (String line : lines) {
                        if (currentLine.length() + line.length() + 1 > maxLength) {
                            if (currentLine.length() > 0) {
                                parts.add(currentLine.toString());
                                currentLine = new StringBuilder();
                            }
                            // If a single line is too long, split it by words
                            if (line.length() > maxLength) {
                                String[] words = line.split(" ");
                                StringBuilder currentWord = new StringBuilder();
                                for (String word : words) {
                                    if (currentWord.length() + word.length() + 1 > maxLength) {
                                        if (currentWord.length() > 0) {
                                            parts.add(currentWord.toString());
                                            currentWord = new StringBuilder();
                                        }
                                        // If a single word is too long, split it by characters
                                        if (word.length() > maxLength) {
                                            for (int i = 0; i < word.length(); i += maxLength) {
                                                parts.add(word.substring(i, Math.min(i + maxLength, word.length())));
                                            }
                                        } else {
                                            currentWord.append(word);
                                        }
                                    } else {
                                        if (currentWord.length() > 0) currentWord.append(" ");
                                        currentWord.append(word);
                                    }
                                }
                                if (currentWord.length() > 0) {
                                    parts.add(currentWord.toString());
                                }
                            } else {
                                currentLine.append(line);
                            }
                        } else {
                            if (currentLine.length() > 0) currentLine.append("\n");
                            currentLine.append(line);
                        }
                    }
                    if (currentLine.length() > 0) {
                        parts.add(currentLine.toString());
                    }
                } else {
                    currentPart.append(section);
                }
            } else {
                if (currentPart.length() > 0) currentPart.append("\n\n");
                currentPart.append(section);
            }
        }
        
        if (currentPart.length() > 0) {
            parts.add(currentPart.toString());
        }
        
        return parts;
    }

    private void sendFallbackMessage(Long chatId) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("Sorry, there was an error formatting the message. Please try again.");
            message.disableWebPagePreview();
            execute(message);
        } catch (TelegramApiException ex) {
            log.error("Failed to send fallback message to chat {}: {}", chatId, ex.getMessage(), ex);
        }
    }

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
