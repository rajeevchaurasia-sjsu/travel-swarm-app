package org.sjsu.travelswarm.service;

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

    private final ConversationService conversationService;

    @Autowired
    public PlanningResultListener(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @RabbitListener(queues = "${app.rabbitmq.results-queue}")
    public void handlePlanningResult(
            FinalItineraryDto itineraryDto,
            Message message,
            @Header(AmqpHeaders.CORRELATION_ID) String correlationId) {

        if (correlationId == null && message.getMessageProperties() != null) {
            correlationId = message.getMessageProperties().getCorrelationId();
        }

        log.info("Received itinerary DTO from queue '{}' with Correlation ID: {}",
                message.getMessageProperties().getConsumerQueue(), correlationId);

        if (itineraryDto == null) {
            log.error("Deserialized itinerary DTO is null for Correlation ID: {}. Payload might be incompatible or empty.", correlationId);
            return;
        }

        try {
            log.info("Deserialized Itinerary DTO: {}", itineraryDto);
            conversationService.handlePlanningResult(correlationId, itineraryDto);
        } catch (Exception e) {
            log.error("Unexpected error during delegation to ConversationService for Correlation ID: {}. Error: {}",
                    correlationId, e.getMessage(), e);
        }
    }
}
