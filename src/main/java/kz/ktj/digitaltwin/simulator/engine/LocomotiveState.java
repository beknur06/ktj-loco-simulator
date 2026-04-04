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
    private double speed = 0;              // км/ч (Норма 0-120)
    private double throttlePosition = 0;   // поз. 0-8 (ТЭ33А) или 0-32 (KZ8A)
    private double tractionForce = 0;      // кН (Норма 0-833)
    private double brakeForce = 0;         // кН (Норма 0-500)

    // --- Temperatures ---
    private double coolantTemp = 75;       // °C (ТЭ33А) (Норма 60-90)
    private double oilTemp = 75;           // °C (ТЭ33А) (Норма 65-85)
    private double exhaustTemp = 400;      // °C (ТЭ33А) (Норма 350-520)
    private double tractionMotorTemp = 90; // °C (оба) (Норма 60-120)
    private double transformerOilTemp = 50;// °C (KZ8A) (Норма 40-75)
    private double ambientTemp = 15;       // °C (Норма -50...+50)

    // --- Pressures ---
    private double oilPressure = 0.45;     // МПа (ТЭ33А) (Норма 0.3-0.6)
    private double brakePipePressure = 0.50;// МПа (оба) (Норма 0.45-0.52)
    private double brakeCylinderPressure = 0;// МПа (оба) (Норма 0-0.40)
    private double boostPressure = 1200;   // мбар (ТЭ33А) (Норма 900-1800)
    private double mainReservoirPressure = 0.85; // МПа (оба) (Норма 0.75-0.90)

    // --- Fuel / Energy ---
    private double fuelLevel = 5500;       // л (ТЭ33А) (Норма 0-6000)
    private double fuelRate = 100;         // л/ч (ТЭ33А) (Норма 30-350)
    private double fuelTemp = 40;          // °C (ТЭ33А) (Норма 20-60)
    private double engineRpm = 600;        // об/мин (ТЭ33А) (Норма 600-1050)

    // --- Electrics ---
    private double catenaryVoltage = 25.0; // кВ (KZ8A) (Норма 21-29)
    private double tractionMotorCurrent = 0; // А (оба) (Норма 0-1200)
    private double dcBusVoltage = 1800;    // В (KZ8A) (Норма 1600-1900)
    private double batteryVoltage = 110;   // В (оба) (Норма 100-120)
    private double regenPower = 0;         // кВт (KZ8A) (Норма 0-7600)

    // --- Aux ---
    private double sandLevel = 85;         // % (Норма 30-100)
    private double cabinTemp = 22;         // °C (Норма 18-25)
    private double gpsLat = 51.1694;       // Астана
    private double gpsLon = 71.4491;
    private double odometer = 0;           // км

    // --- Diagnostics ---
    private String errorCode = null;       // Код ошибки DTC
    private String signalingState = "GREEN"; // Сигнал КЛУБ-У / АЛСН

    // --- Anomaly ---
    private AnomalyType activeAnomaly = AnomalyType.NONE;
    private int anomalyTicksRemaining = 0;
    private double anomalyIntensity = 0;   // 0..1, нарастает
}
