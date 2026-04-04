package kz.ktj.digitaltwin.simulator.engine;

import kz.ktj.digitaltwin.simulator.model.AnomalyType;
import kz.ktj.digitaltwin.simulator.model.DrivingPhase;
import kz.ktj.digitaltwin.simulator.model.LocomotiveType;
import lombok.Data;

/**
 * Полное мутабельное состояние одного локомотива.
 * Обновляется каждый тик (1 сек) движком симуляции.
 *
 * Все параметры хранятся как «текущее точное значение»,
 * к которому при генерации сообщения добавляется шум.
 */
@Data
public class LocomotiveState {

    // --- Identity ---
    private String locomotiveId;
    private LocomotiveType type;
    private String routeId;

    // --- Driving ---
    private DrivingPhase phase = DrivingPhase.STARTUP;
    private int phaseTicksRemaining = 30;  // сколько тиков осталось в текущей фазе
    private double speed = 0;              // км/ч
    private double throttlePosition = 0;   // 0..1 (нормализовано)
    private double tractionForce = 0;      // кН
    private double brakeForce = 0;         // кН

    // --- Temperatures ---
    private double coolantTemp = 45;       // °C (ТЭ33А)
    private double oilTemp = 40;           // °C (ТЭ33А)
    private double exhaustTemp = 200;      // °C (ТЭ33А)
    private double tractionMotorTemp = 45; // °C (оба)
    private double transformerOilTemp = 35;// °C (KZ8A)
    private double ambientTemp = 15;       // °C

    // --- Pressures ---
    private double oilPressure = 0.45;     // МПа (ТЭ33А)
    private double brakePipePressure = 0.50;// МПа
    private double brakeCylinderPressure = 0;// МПа
    private double boostPressure = 1013;   // мбар (ТЭ33А)
    private double mainReservoirPressure = 0.85; // МПа

    // --- Fuel / Energy ---
    private double fuelLevel = 5500;       // л (ТЭ33А)
    private double fuelRate = 0;           // л/ч (ТЭ33А)
    private double fuelTemp = 25;          // °C (ТЭ33А)
    private double engineRpm = 0;          // об/мин (ТЭ33А)

    // --- Electrics ---
    private double catenaryVoltage = 25.0; // кВ (KZ8A)
    private double tractionMotorCurrent = 0; // А
    private double dcBusVoltage = 1800;    // В (KZ8A)
    private double batteryVoltage = 110;   // В
    private double regenPower = 0;         // кВт (KZ8A)

    // --- Aux ---
    private double sandLevel = 85;         // %
    private double cabinTemp = 22;         // °C
    private double gpsLat = 51.1694;       // Астана
    private double gpsLon = 71.4491;
    private double odometer = 0;           // км

    // --- Anomaly ---
    private AnomalyType activeAnomaly = AnomalyType.NONE;
    private int anomalyTicksRemaining = 0;
    private double anomalyIntensity = 0;   // 0..1, нарастает
}
