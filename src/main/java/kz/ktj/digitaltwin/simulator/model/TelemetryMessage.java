package kz.ktj.digitaltwin.simulator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TelemetryMessage {

    private String locomotiveId;
    private LocomotiveType locomotiveType;
    private Instant timestamp;
    private DrivingPhase phase;

    /**
     * Все параметры в одном сообщении.
     * Ключ — код параметра (speed, coolant_temp, ...),
     * Значение — числовое значение.
     */
    private Map<String, Double> parameters;

    /**
     * Активные ошибки/алерты (коды DTC).
     * Пустой список = всё ок.
     */
    private List<String> activeDtcCodes;

    /**
     * GPS-координаты текущей позиции.
     */
    private Double gpsLat;
    private Double gpsLon;

    /**
     * Метаданные маршрута.
     */
    private String routeId;
    private Double odometer;
}
