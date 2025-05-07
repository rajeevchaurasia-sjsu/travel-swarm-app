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

    public void sendTextMessage(long chatId, String rawText) {
        if (rawText == null || rawText.isBlank()) {
            log.warn("Attempted to send null or blank message to chatId {}", chatId);
            return;
        }

        // Escape the text first
        String escapedText = escapeMarkdownV2(rawText);

        if (escapedText.length() <= MAX_MESSAGE_LENGTH) {
            executeSendMessage(chatId, escapedText);
        } else {
            // Message is too long, split it
            log.info("Message for chatId {} is too long ({} chars). Splitting...", chatId, escapedText.length());
            List<String> parts = splitMessage(escapedText, MAX_MESSAGE_LENGTH);
            for (int i = 0; i < parts.size(); i++) {
                String part = parts.get(i);
                executeSendMessage(chatId, part);
                // Slight delay between messages to avoid rate limiting and ensure order
                if (i < parts.size() - 1) {
                    try {
                        Thread.sleep(500); // 0.5 second delay
                    } catch (InterruptedException e) {
                        log.warn("Message sending delay interrupted for chatId {}", chatId);
                        Thread.currentThread().interrupt();
                    }
                }
            }
            log.info("Sent {} parts for long message to chatId {}", parts.size(), chatId);
        }
    }

    private void executeSendMessage(long chatId, String text) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("MarkdownV2")
                .build();
        try {
            log.info("Sending message to chatId {}: '{}'", chatId, text.substring(0, Math.min(text.length(), 100)) + (text.length() > 100 ? "..." : ""));
            execute(sendMessage);
            log.debug("Message part sent successfully to chatId {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send message part to chatId {}: {}", chatId, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending message part to chatId {}: {}", chatId, e.getMessage(), e);
        }
    }

    private List<String> splitMessage(String text, int maxLength) {
        List<String> parts = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return parts;
        }

        // Split by double newlines to preserve formatting
        String[] sections = text.split("\n\n", -1);
        StringBuilder currentPart = new StringBuilder();

        for (String section : sections) {
            // If adding this section would exceed maxLength, start a new part
            if (!currentPart.isEmpty() && currentPart.length() + section.length() + 2 > maxLength) {
                parts.add(currentPart.toString().trim());
                currentPart = new StringBuilder();
            }

            // If section itself is too long, split it by single newlines
            if (section.length() > maxLength) {
                String[] lines = section.split("\n", -1);
                for (String line : lines) {
                    // Check if line contains Markdown formatting
                    boolean hasMarkdown = line.contains("*") || line.contains("_") || line.contains("[") || line.contains("]");
                    
                    if (line.length() > maxLength) {
                        // If currentPart has content, add it first
                        if (!currentPart.isEmpty()) {
                            parts.add(currentPart.toString().trim());
                            currentPart = new StringBuilder();
                        }
                        
                        // For lines with Markdown, try to split at formatting boundaries
                        if (hasMarkdown) {
                            int start = 0;
                            while (start < line.length()) {
                                int end = Math.min(start + maxLength, line.length());
                                if (end < line.length()) {
                                    // Look for the last formatting boundary before maxLength
                                    int lastFormat = Math.max(
                                        Math.max(line.lastIndexOf("*", end), line.lastIndexOf("_", end)),
                                        Math.max(line.lastIndexOf("[", end), line.lastIndexOf("]", end))
                                    );
                                    if (lastFormat > start + maxLength / 2) {
                                        end = lastFormat;
                                    } else {
                                        // Fall back to space or punctuation
                                        int lastSpace = line.lastIndexOf(' ', end);
                                        int lastPunct = Math.max(
                                            Math.max(line.lastIndexOf('.', end), line.lastIndexOf(',', end)),
                                            Math.max(line.lastIndexOf('!', end), line.lastIndexOf('?', end))
                                        );
                                        int breakPoint = Math.max(lastSpace, lastPunct);
                                        if (breakPoint > start + maxLength / 2) {
                                            end = breakPoint + 1;
                                        }
                                    }
                                }
                                parts.add(line.substring(start, end).trim());
                                start = end;
                            }
                        } else {
                            // For regular lines, split at spaces or punctuation
                            int start = 0;
                            while (start < line.length()) {
                                int end = Math.min(start + maxLength, line.length());
                                if (end < line.length()) {
                                    int lastSpace = line.lastIndexOf(' ', end);
                                    int lastPunct = Math.max(
                                        Math.max(line.lastIndexOf('.', end), line.lastIndexOf(',', end)),
                                        Math.max(line.lastIndexOf('!', end), line.lastIndexOf('?', end))
                                    );
                                    int breakPoint = Math.max(lastSpace, lastPunct);
                                    if (breakPoint > start + maxLength / 2) {
                                        end = breakPoint + 1;
                                    }
                                }
                                parts.add(line.substring(start, end).trim());
                                start = end;
                            }
                        }
                    } else {
                        if (!currentPart.isEmpty() && currentPart.length() + line.length() + 1 > maxLength) {
                            parts.add(currentPart.toString().trim());
                            currentPart = new StringBuilder();
                        }
                        if (!currentPart.isEmpty()) {
                            currentPart.append("\n");
                        }
                        currentPart.append(line);
                    }
                }
            } else {
                if (!currentPart.isEmpty()) {
                    currentPart.append("\n\n");
                }
                currentPart.append(section);
            }
        }

        // Add any remaining part
        if (!currentPart.isEmpty()) {
            parts.add(currentPart.toString().trim());
        }

        return parts;
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

    private String escapeMarkdownV2(String text) {
        if (text == null) return null;
        
        // First escape all special characters
        String escaped = MARKDOWN_PATTERN.matcher(text).replaceAll("\\\\$0");
        
        // Then handle formatting characters
        // We need to be careful with the order of replacements
        return escaped
            .replace("\\*", "*")  // Bold
            .replace("\\_", "_")  // Italic
            .replace("\\[", "[")  // Links
            .replace("\\]", "]")
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\~", "~")  // Strikethrough
            .replace("\\`", "`")  // Code
            // Keep other special characters escaped
            .replace("\\-", "\\-")  // Keep dash escaped
            .replace("\\.", "\\.")  // Keep dot escaped
            .replace("\\!", "\\!")  // Keep exclamation escaped
            .replace("\\#", "\\#")  // Keep hash escaped
            .replace("\\+", "\\+")  // Keep plus escaped
            .replace("\\=", "\\=")  // Keep equals escaped
            .replace("\\|", "\\|")  // Keep pipe escaped
            .replace("\\{", "\\{")  // Keep braces escaped
            .replace("\\}", "\\}")  // Keep braces escaped
            .replace("\\>", "\\>"); // Keep greater than escaped
    }
}
