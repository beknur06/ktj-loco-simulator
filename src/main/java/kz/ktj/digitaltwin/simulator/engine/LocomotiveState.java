package kz.ktj.digitaltwin.simulator.engine;

import kz.ktj.digitaltwin.simulator.model.AnomalyType;
import kz.ktj.digitaltwin.simulator.model.DrivingPhase;
import kz.ktj.digitaltwin.simulator.model.LocomotiveType;
import lombok.Data;

@Data
public class LocomotiveState {

    private String locomotiveId;
    private LocomotiveType type;
    private String routeId;

    private DrivingPhase phase = DrivingPhase.STARTUP;
    private int phaseTicksRemaining = 30;
    private double speed = 0;
    private double throttlePosition = 0;
    private double tractionForce = 0;
    private double brakeForce = 0;

    private double coolantTemp = 75;
    private double oilTemp = 75;
    private double exhaustTemp = 400;
    private double tractionMotorTemp = 90;
    private double transformerOilTemp = 50;
    private double ambientTemp = 15;

    private double oilPressure = 0.45;
    private double brakePipePressure = 0.50;
    private double brakeCylinderPressure = 0;
    private double boostPressure = 1200;
    private double mainReservoirPressure = 0.85;

    private double fuelLevel = 5500;
    private double fuelRate = 100;
    private double fuelTemp = 40;
    private double engineRpm = 600;

    private double catenaryVoltage = 25.0;
    private double tractionMotorCurrent = 0;
    private double dcBusVoltage = 1800;
    private double batteryVoltage = 110;
    private double regenPower = 0;

    private double sandLevel = 85;
    private double cabinTemp = 22;
    private double gpsLat = 51.1694;
    private double gpsLon = 71.4491;
    private double odometer = 0;

    private String errorCode = null;
    private String signalingState = "GREEN";

    private AnomalyType activeAnomaly = AnomalyType.NONE;
    private int anomalyTicksRemaining = 0;
    private double anomalyIntensity = 0;
}
