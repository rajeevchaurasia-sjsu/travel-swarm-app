package org.sjsu.travelswarm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.sjsu.travelswarm.model.dto.FinalItineraryDto;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PlanningResultListener {

    @Autowired
    private ObjectMapper objectMapper;

    @RabbitListener(queues = "${app.rabbitmq.results-queue}")
    public void handlePlanningResult(String messagePayload, Message message,
                                     @Header(AmqpHeaders.CORRELATION_ID) String correlationId) {

        log.info("Received itinerary result from queue '{}' with Correlation ID: {}",
                message.getMessageProperties().getConsumerQueue(), correlationId);
        log.debug("Raw Payload: {}", messagePayload);

        try {
            // Deserialize the JSON payload string into our FinalItineraryDto object
            FinalItineraryDto itinerary = objectMapper.readValue(messagePayload, FinalItineraryDto.class);

            log.info("Successfully deserialized itinerary for Destination: {}, Correlation ID: {}",
                    itinerary.getDestination(), correlationId);
            log.debug("Deserialized Itinerary DTO: {}", itinerary);

            // --- TODO: Process the received itinerary ---
            // 1. Find the original request/user context using the correlationId (requires state management)
            // 2. Save the itinerary to the PostgreSQL database (using a new ItineraryStorageService)
            // 3. Format the itinerary nicely for Telegram
            // 4. Send the formatted itinerary back to the correct user via Telegram (using a Telegram service)
            // 5. Clean up conversation state
            log.warn("TODO: Implement itinerary processing logic (DB save, Telegram notification) for Correlation ID: {}", correlationId);
            // ---------------------------------------------

            // Basic acknowledgment is handled automatically by Spring AMQP on successful method execution
            // If an exception is thrown, the message is typically rejected/requeued based on config

        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize itinerary JSON for Correlation ID: {}. Payload: {}. Error: {}",
                    correlationId, messagePayload, e.getMessage(), e);
        } catch (Exception e) {
            log.error("An unexpected error occurred processing itinerary result for Correlation ID: {}. Error: {}",
                    correlationId, e.getMessage(), e);
        }
    }
}
