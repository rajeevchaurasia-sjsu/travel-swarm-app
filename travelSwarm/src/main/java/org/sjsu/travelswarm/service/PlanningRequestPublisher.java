package org.sjsu.travelswarm.service;

import lombok.extern.slf4j.Slf4j;
import org.sjsu.travelswarm.model.dto.PlanningRequestDto;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class PlanningRequestPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.planning-request-queue}")
    private String planningRequestQueueName;

    /**
     * Publishes a planning request to the RabbitMQ queue.
     *
     * @param requestDto    The planning request data.
     * @param correlationId A unique ID to track the request and its corresponding result.
     */
    public void publishRequest(PlanningRequestDto requestDto, String correlationId) {
        if (requestDto == null) {
            log.warn("Attempted to publish a null PlanningRequestDto. Aborting.");
            return;
        }

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.warn("Publishing request without a provided correlationId, generated: {}", correlationId);
        }

        final String finalCorrelationId = correlationId; // Variable used in lambda needs to be final

        log.info("Publishing planning request to queue '{}' with Correlation ID: {}", planningRequestQueueName, finalCorrelationId);
        log.debug("Request Payload: {}", requestDto);

        try {
            rabbitTemplate.convertAndSend(planningRequestQueueName, requestDto, message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                message.getMessageProperties().setCorrelationId(finalCorrelationId);
                return message;
            });
            log.info("Successfully published request with Correlation ID: {}", finalCorrelationId);
        } catch (AmqpException e) {
            log.error("Failed to publish planning request with Correlation ID: {}. Error: {}", finalCorrelationId, e.getMessage(), e);
        } catch (Exception e) {
            log.error("An unexpected error occurred during publishing for Correlation ID: {}. Error: {}", finalCorrelationId, e.getMessage(), e);
        }
    }

}
