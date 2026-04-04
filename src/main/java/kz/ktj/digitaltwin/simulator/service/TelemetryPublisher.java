package kz.ktj.digitaltwin.simulator.service;

import kz.ktj.digitaltwin.simulator.model.TelemetryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Отправляет телеметрию по HTTP POST в Ingestion Service.
 *
 * Симулятор ведёт себя как реальный локомотив:
 *   POST http://ingestion-service:8081/api/v1/telemetry
 *
 * Ingestion Service отвечает за валидацию и публикацию в RabbitMQ.
 * Такой подход позволяет заменить симулятор на реальный локомотив
 * без изменений в downstream-сервисах.
 */
@Service
public class TelemetryPublisher {

    private static final Logger log = LoggerFactory.getLogger(TelemetryPublisher.class);

    private final RestClient restClient;

    public TelemetryPublisher(
            @Value("${ingestion.service.url:http://localhost:8081}") String ingestionUrl) {
        this.restClient = RestClient.builder()
            .baseUrl(ingestionUrl)
            .build();
    }

    /**
     * POST телеметрии в Ingestion Service.
     */
    public void publish(TelemetryMessage message) {
        try {
            restClient.post()
                .uri("/api/v1/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .body(message)
                .retrieve()
                .toBodilessEntity();

            log.trace("Sent telemetry for {} [speed={}, phase={}]",
                message.getLocomotiveId(),
                message.getParameters().get("speed"),
                message.getPhase());
        } catch (Exception e) {
            log.warn("Failed to send telemetry for {}: {}",
                message.getLocomotiveId(), e.getMessage());
        }
    }
}
