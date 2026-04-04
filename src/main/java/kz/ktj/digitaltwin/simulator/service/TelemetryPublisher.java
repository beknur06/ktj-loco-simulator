package kz.ktj.digitaltwin.simulator.service;

import kz.ktj.digitaltwin.simulator.model.TelemetryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TelemetryPublisher {

    private static final Logger log = LoggerFactory.getLogger(TelemetryPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKeyPrefix;

    public TelemetryPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${rabbitmq.exchange.telemetry}") String exchange,
            @Value("${rabbitmq.routing-key.prefix}") String routingKeyPrefix) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKeyPrefix = routingKeyPrefix;
    }

    /**
     * Публикация телеметрии в RabbitMQ.
     * Routing key: telemetry.{locomotiveId}
     */
    public void publish(TelemetryMessage message) {
        String routingKey = routingKeyPrefix + "." + message.getLocomotiveId();

        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            log.trace("Published telemetry for {} [speed={}, phase={}]",
                message.getLocomotiveId(),
                message.getParameters().get("speed"),
                message.getPhase());
        } catch (Exception e) {
            log.error("Failed to publish telemetry for {}: {}",
                message.getLocomotiveId(), e.getMessage());
        }
    }
}
