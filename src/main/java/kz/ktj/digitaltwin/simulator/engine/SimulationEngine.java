package kz.ktj.digitaltwin.simulator.engine;

import kz.ktj.digitaltwin.simulator.model.AnomalyType;
import kz.ktj.digitaltwin.simulator.model.DrivingPhase;
import kz.ktj.digitaltwin.simulator.model.LocomotiveType;
import kz.ktj.digitaltwin.simulator.model.TelemetryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Движок симуляции одного локомотива.
 *
 * Каждый вызов tick() продвигает состояние на 1 секунду:
 *  1. Обновляет фазу движения (state machine)
 *  2. Рассчитывает целевые значения параметров по фазе
 *  3. Плавно сдвигает текущие значения к целевым (инерция)
 *  4. Применяет аномалии (если активны)
 *  5. Добавляет шум
 *  6. Формирует TelemetryMessage
 */
public class SimulationEngine {

    private static final Logger log = LoggerFactory.getLogger(SimulationEngine.class);
    private static final ThreadLocalRandom RNG = ThreadLocalRandom.current();

    private final LocomotiveState state;
    private final double anomalyProbability;

    // Real KTZ railway route: Астана-1 → Қарағанды → Балқаш → Алматы-2
    // Total ~973 km by rail
    private static final double[][] ROUTE_ASTANA_ALMATY = {
        {51.1956, 71.4089},  // Астана-1     (km 0)
        {49.7870, 73.0980},  // Қарағанды    (km 190)
        {46.8500, 74.9900},  // Балқаш-2     (km 420)
        {43.2740, 76.9390},  // Алматы-2     (km 973)
    };
    // Per-segment real distances (km): Astana→Karaganda, Karaganda→Balkash, Balkash→Almaty
    private static final double[] ROUTE_ASTANA_ALMATY_SEGMENTS = {190.0, 230.0, 553.0};

    // Real KTZ railway route: Астана-1 → Қарағанды (direct, ~190 km)
    private static final double[][] ROUTE_ASTANA_KARAGANDA = {
        {51.1956, 71.4089},  // Астана-1     (km 0)
        {49.7870, 73.0980},  // Қарағанды    (km 190)
    };
    private static final double[] ROUTE_ASTANA_KARAGANDA_SEGMENTS = {190.0};

    private int routeSegment = 0;
    private double segmentProgress = 0;

    public SimulationEngine(LocomotiveState state, double anomalyProbability) {
        this.state = state;
        this.anomalyProbability = anomalyProbability;
    }

    /**
     * Один тик симуляции (1 секунда).
     * Возвращает готовое сообщение телеметрии.
     */
    public TelemetryMessage tick() {
        updatePhase();
        updateDrivingParameters();
        updateTemperatures();
        updatePressures();
        updateFuelAndEnergy();
        updateElectrics();
        updatePosition();
        updateAnomalies();

        return buildMessage();
    }

    // ═══════════════════════════════════════════════════
    // Phase state machine
    // ═══════════════════════════════════════════════════

    private void updatePhase() {
        state.setPhaseTicksRemaining(state.getPhaseTicksRemaining() - 1);

        if (state.getPhaseTicksRemaining() <= 0) {
            transitionToNextPhase();
        }
    }

    private void transitionToNextPhase() {
        DrivingPhase current = state.getPhase();
        DrivingPhase next;
        int duration;

        switch (current) {
            case STARTUP -> {
                next = DrivingPhase.ACCELERATING;
                duration = randInt(30, 90); // 30-90 сек на разгон
            }
            case ACCELERATING -> {
                next = DrivingPhase.CRUISING;
                duration = randInt(120, 600); // 2-10 мин крейсер
            }
            case CRUISING -> {
                // 70% шанс продолжить, 20% выбег, 10% торможение
                double r = RNG.nextDouble();
                if (r < 0.7) {
                    next = DrivingPhase.CRUISING;
                    duration = randInt(60, 300);
                } else if (r < 0.9) {
                    next = DrivingPhase.COASTING;
                    duration = randInt(20, 60);
                } else {
                    next = DrivingPhase.BRAKING;
                    duration = randInt(20, 60);
                }
            }
            case COASTING -> {
                double r = RNG.nextDouble();
                if (r < 0.5) {
                    next = DrivingPhase.ACCELERATING;
                    duration = randInt(20, 60);
                } else {
                    next = DrivingPhase.BRAKING;
                    duration = randInt(20, 50);
                }
            }
            case BRAKING -> {
                next = DrivingPhase.STOPPED;
                duration = randInt(30, 180); // стоянка 30сек - 3мин
            }
            case STOPPED -> {
                next = DrivingPhase.ACCELERATING;
                duration = randInt(40, 90);
            }
            default -> {
                next = DrivingPhase.ACCELERATING;
                duration = 60;
            }
        }

        state.setPhase(next);
        state.setPhaseTicksRemaining(duration);
        log.debug("[{}] Phase: {} → {} ({}s)", state.getLocomotiveId(), current, next, duration);
    }

    // ═══════════════════════════════════════════════════
    // Driving parameters (speed, throttle, forces)
    // ═══════════════════════════════════════════════════

    private void updateDrivingParameters() {
        double maxSpeed = 120.0;
        double targetSpeed;
        double targetThrottle;

        switch (state.getPhase()) {
            case STARTUP -> {
                targetSpeed = 0;
                targetThrottle = 0;
            }
            case ACCELERATING -> {
                targetSpeed = randDouble(60, maxSpeed);
                targetThrottle = randDouble(0.5, 1.0);
            }
            case CRUISING -> {
                targetSpeed = randDouble(70, 100);
                targetThrottle = randDouble(0.3, 0.6);
            }
            case COASTING -> {
                targetSpeed = state.getSpeed() * 0.98; // медленное замедление
                targetThrottle = 0;
            }
            case BRAKING -> {
                targetSpeed = Math.max(0, state.getSpeed() - randDouble(1, 3));
                targetThrottle = 0;
            }
            case STOPPED -> {
                targetSpeed = 0;
                targetThrottle = 0;
            }
            default -> {
                targetSpeed = 0;
                targetThrottle = 0;
            }
        }

        // Плавная инерция: 10% сдвиг к цели за тик
        state.setSpeed(lerp(state.getSpeed(), targetSpeed, 0.1));
        state.setThrottlePosition(lerp(state.getThrottlePosition(), targetThrottle, 0.15));

        // Тяговое усилие пропорционально позиции контроллера
        double maxTraction = state.getType() == LocomotiveType.KZ8A ? 833.0 : 500.0;
        state.setTractionForce(state.getThrottlePosition() * maxTraction * randDouble(0.85, 1.0));

        // Тормозное усилие
        if (state.getPhase() == DrivingPhase.BRAKING) {
            state.setBrakeForce(lerp(state.getBrakeForce(), randDouble(100, 350), 0.1));
        } else {
            state.setBrakeForce(lerp(state.getBrakeForce(), 0, 0.2));
        }
    }

    // ═══════════════════════════════════════════════════
    // Temperatures (correlated with load)
    // ═══════════════════════════════════════════════════

    private void updateTemperatures() {
        double load = state.getThrottlePosition(); // 0..1

        if (state.getType() == LocomotiveType.TE33A) {
            // Температура охлаждающей жидкости: целевая 70-85 при нагрузке
            double targetCoolant = 55 + load * 30 + state.getAmbientTemp() * 0.1;
            state.setCoolantTemp(lerp(state.getCoolantTemp(), targetCoolant, 0.02));

            // Температура масла: следует за охлаждающей, но с отставанием
            double targetOilTemp = state.getCoolantTemp() - 5 + load * 10;
            state.setOilTemp(lerp(state.getOilTemp(), targetOilTemp, 0.015));

            // Выхлопные газы: быстро реагируют на нагрузку
            double targetExhaust = 200 + load * 350;
            state.setExhaustTemp(lerp(state.getExhaustTemp(), targetExhaust, 0.08));
        }

        if (state.getType() == LocomotiveType.KZ8A) {
            // Температура масла трансформатора
            double targetTransOil = 35 + load * 45 + state.getAmbientTemp() * 0.15;
            state.setTransformerOilTemp(lerp(state.getTransformerOilTemp(), targetTransOil, 0.01));
        }

        // Обмотки ТЭД: для обоих типов
        double targetMotorTemp = 45 + load * 90 + state.getAmbientTemp() * 0.2;
        state.setTractionMotorTemp(lerp(state.getTractionMotorTemp(), targetMotorTemp, 0.015));

        // Внешняя температура: медленный дрейф
        state.setAmbientTemp(state.getAmbientTemp() + randDouble(-0.01, 0.01));
        state.setAmbientTemp(clamp(state.getAmbientTemp(), -40, 45));
    }

    // ═══════════════════════════════════════════════════
    // Pressures
    // ═══════════════════════════════════════════════════

    private void updatePressures() {
        double load = state.getThrottlePosition();

        if (state.getType() == LocomotiveType.TE33A) {
            // Давление масла: зависит от оборотов
            double rpmFactor = state.getEngineRpm() / 1050.0;
            double targetOilPressure = 0.25 + rpmFactor * 0.30;
            state.setOilPressure(lerp(state.getOilPressure(), targetOilPressure, 0.05));

            // Давление наддува: зависит от нагрузки
            double targetBoost = 1013 + load * 800;
            state.setBoostPressure(lerp(state.getBoostPressure(), targetBoost, 0.06));
        }

        // Тормозная магистраль: стабильна в норме
        double targetBrakePipe = 0.50;
        if (state.getPhase() == DrivingPhase.BRAKING) {
            targetBrakePipe = 0.38 + randDouble(0, 0.05);
        }
        state.setBrakePipePressure(lerp(state.getBrakePipePressure(), targetBrakePipe, 0.05));

        // Тормозной цилиндр: давление при торможении
        double targetBrakeCylinder = state.getPhase() == DrivingPhase.BRAKING ? 0.25 + randDouble(0, 0.1) : 0;
        state.setBrakeCylinderPressure(lerp(state.getBrakeCylinderPressure(), targetBrakeCylinder, 0.1));

        // Главный резервуар: компрессор поддерживает
        double targetReservoir = 0.85;
        if (state.getPhase() == DrivingPhase.BRAKING) {
            targetReservoir = 0.78; // расход воздуха при торможении
        }
        state.setMainReservoirPressure(lerp(state.getMainReservoirPressure(), targetReservoir, 0.02));
    }

    // ═══════════════════════════════════════════════════
    // Fuel and energy (ТЭ33А specific)
    // ═══════════════════════════════════════════════════

    private void updateFuelAndEnergy() {
        if (state.getType() != LocomotiveType.TE33A) return;

        double load = state.getThrottlePosition();

        // Обороты дизеля: 600 (холостой ход) до 1050 (полная нагрузка)
        double targetRpm;
        if (state.getPhase() == DrivingPhase.STOPPED || state.getPhase() == DrivingPhase.STARTUP) {
            targetRpm = state.getPhase() == DrivingPhase.STOPPED ? 600 : 650;
        } else {
            targetRpm = 600 + load * 450;
        }
        state.setEngineRpm(lerp(state.getEngineRpm(), targetRpm, 0.08));

        // Расход топлива: 30 л/ч (хол. ход) до 350 л/ч (полная тяга)
        double targetFuelRate = 30 + load * 320;
        state.setFuelRate(lerp(state.getFuelRate(), targetFuelRate, 0.1));

        // Уровень топлива: уменьшается по расходу
        double consumedPerTick = state.getFuelRate() / 3600.0; // л/сек
        state.setFuelLevel(Math.max(0, state.getFuelLevel() - consumedPerTick));

        // Температура топлива: растёт с нагрузкой и температурой
        double targetFuelTemp = state.getAmbientTemp() + 10 + load * 20;
        state.setFuelTemp(lerp(state.getFuelTemp(), targetFuelTemp, 0.01));
    }

    // ═══════════════════════════════════════════════════
    // Electrics
    // ═══════════════════════════════════════════════════

    private void updateElectrics() {
        double load = state.getThrottlePosition();

        // Ток ТЭД: пропорционален нагрузке
        double maxCurrent = state.getType() == LocomotiveType.KZ8A ? 1200 : 800;
        double targetCurrent = load * maxCurrent;
        state.setTractionMotorCurrent(lerp(state.getTractionMotorCurrent(), targetCurrent, 0.1));

        if (state.getType() == LocomotiveType.KZ8A) {
            // Напряжение контактной сети: дрейф ~25 кВ
            state.setCatenaryVoltage(25.0 + randDouble(-0.8, 0.8));

            // Шина DC
            state.setDcBusVoltage(1800 + randDouble(-50, 50));

            // Рекуперация при торможении
            if (state.getPhase() == DrivingPhase.BRAKING) {
                double regenTarget = state.getSpeed() / 120.0 * 7600;
                state.setRegenPower(lerp(state.getRegenPower(), regenTarget, 0.1));
            } else {
                state.setRegenPower(lerp(state.getRegenPower(), 0, 0.2));
            }
        }

        // АКБ: медленный дрейф
        state.setBatteryVoltage(110 + randDouble(-3, 3));
    }

    // ═══════════════════════════════════════════════════
    // GPS position (simplified route interpolation)
    // ═══════════════════════════════════════════════════

    private void updatePosition() {
        if (state.getSpeed() < 1) return;

        boolean isAlmaty = state.getRouteId().contains("ALMATY");
        double[][] route    = isAlmaty ? ROUTE_ASTANA_ALMATY    : ROUTE_ASTANA_KARAGANDA;
        double[]   segments = isAlmaty ? ROUTE_ASTANA_ALMATY_SEGMENTS : ROUTE_ASTANA_KARAGANDA_SEGMENTS;

        // Продвигаем одометр пропорционально скорости (км/с)
        double kmPerTick = state.getSpeed() / 3600.0;
        state.setOdometer(state.getOdometer() + kmPerTick);

        // Сегментный прогресс с учётом реальной длины каждого сегмента
        double currentSegmentKm = (routeSegment < segments.length) ? segments[routeSegment] : segments[segments.length - 1];
        segmentProgress += kmPerTick / currentSegmentKm;

        if (segmentProgress >= 1.0 && routeSegment < route.length - 2) {
            routeSegment++;
            segmentProgress = 0;
        }

        int fromIdx = Math.min(routeSegment, route.length - 2);
        int toIdx   = fromIdx + 1;
        double t    = Math.min(segmentProgress, 1.0);

        state.setGpsLat(lerp(route[fromIdx][0], route[toIdx][0], t));
        state.setGpsLon(lerp(route[fromIdx][1], route[toIdx][1], t));
    }

    // ═══════════════════════════════════════════════════
    // Anomaly injection & effects
    // ═══════════════════════════════════════════════════

    private void updateAnomalies() {
        // Аномалия уже идёт?
        if (state.getActiveAnomaly() != AnomalyType.NONE) {
            state.setAnomalyTicksRemaining(state.getAnomalyTicksRemaining() - 1);
            // Интенсивность нарастает первые 30% времени, потом спадает
            double totalDuration = state.getAnomalyTicksRemaining() + state.getAnomalyIntensity() * 100;
            state.setAnomalyIntensity(Math.min(1.0, state.getAnomalyIntensity() + 0.02));

            applyAnomalyEffects();

            if (state.getAnomalyTicksRemaining() <= 0) {
                log.info("[{}] Anomaly {} ended", state.getLocomotiveId(), state.getActiveAnomaly());
                state.setActiveAnomaly(AnomalyType.NONE);
                state.setAnomalyIntensity(0);
            }
            return;
        }

        // Случайно инжектировать новую аномалию?
        if (RNG.nextDouble() < anomalyProbability) {
            AnomalyType anomaly = pickAnomaly();
            int duration = randInt(30, 120);
            state.setActiveAnomaly(anomaly);
            state.setAnomalyTicksRemaining(duration);
            state.setAnomalyIntensity(0);
            log.info("[{}] Anomaly injected: {} ({}s)", state.getLocomotiveId(), anomaly, duration);
        }
    }

    private AnomalyType pickAnomaly() {
        List<AnomalyType> candidates = new ArrayList<>();
        candidates.add(AnomalyType.TRACTION_MOTOR_OVERHEAT);
        candidates.add(AnomalyType.BRAKE_PIPE_LEAK);

        if (state.getType() == LocomotiveType.TE33A) {
            candidates.add(AnomalyType.COOLANT_OVERHEAT);
            candidates.add(AnomalyType.OIL_PRESSURE_DROP);
            candidates.add(AnomalyType.FUEL_LEAK);
            candidates.add(AnomalyType.EXHAUST_TEMP_SPIKE);
        }

        if (state.getType() == LocomotiveType.KZ8A) {
            candidates.add(AnomalyType.CATENARY_VOLTAGE_SAG);
        }

        return candidates.get(RNG.nextInt(candidates.size()));
    }

    private void applyAnomalyEffects() {
        double intensity = state.getAnomalyIntensity();

        switch (state.getActiveAnomaly()) {
            case COOLANT_OVERHEAT -> {
                // Температура охлаждающей жидкости уходит за 95°C
                state.setCoolantTemp(state.getCoolantTemp() + intensity * 0.5);
            }
            case OIL_PRESSURE_DROP -> {
                // Давление масла падает ниже 0.15 МПа
                state.setOilPressure(state.getOilPressure() - intensity * 0.005);
                state.setOilPressure(Math.max(0.05, state.getOilPressure()));
            }
            case BRAKE_PIPE_LEAK -> {
                // Утечка: давление медленно падает
                state.setBrakePipePressure(state.getBrakePipePressure() - intensity * 0.003);
                state.setBrakePipePressure(Math.max(0.2, state.getBrakePipePressure()));
            }
            case TRACTION_MOTOR_OVERHEAT -> {
                // Обмотки ТЭД перегреваются
                state.setTractionMotorTemp(state.getTractionMotorTemp() + intensity * 0.8);
            }
            case FUEL_LEAK -> {
                // Ускоренный расход топлива
                state.setFuelLevel(state.getFuelLevel() - intensity * 0.5);
            }
            case CATENARY_VOLTAGE_SAG -> {
                // Просадка напряжения сети
                state.setCatenaryVoltage(25.0 - intensity * 7);
            }
            case EXHAUST_TEMP_SPIKE -> {
                // Скачок выхлопных газов
                state.setExhaustTemp(state.getExhaustTemp() + intensity * 2);
            }
            default -> {}
        }
    }

    // ═══════════════════════════════════════════════════
    // Build outgoing message
    // ═══════════════════════════════════════════════════

    private TelemetryMessage buildMessage() {
        Map<String, Double> params = new LinkedHashMap<>();

        // --- Movement ---
        params.put("speed", round(state.getSpeed()));
        params.put("traction_force", round(state.getTractionForce()));
        params.put("brake_force", round(state.getBrakeForce()));
        params.put("throttle_pos", round(state.getThrottlePosition() * getMaxThrottle()));

        // --- Temperatures ---
        params.put("traction_motor_temp", round(addNoise(state.getTractionMotorTemp(), 0.5)));
        params.put("ambient_temp", round(addNoise(state.getAmbientTemp(), 0.2)));

        if (state.getType() == LocomotiveType.TE33A) {
            params.put("coolant_temp", round(addNoise(state.getCoolantTemp(), 0.3)));
            params.put("oil_temp", round(addNoise(state.getOilTemp(), 0.3)));
            params.put("exhaust_temp", round(addNoise(state.getExhaustTemp(), 2)));
        }
        if (state.getType() == LocomotiveType.KZ8A) {
            params.put("transformer_oil_temp", round(addNoise(state.getTransformerOilTemp(), 0.3)));
        }

        // --- Pressures ---
        params.put("brake_pipe_pressure", round(addNoise(state.getBrakePipePressure(), 0.005)));
        params.put("brake_cylinder_pressure", round(addNoise(state.getBrakeCylinderPressure(), 0.005)));
        params.put("main_reservoir_pressure", round(addNoise(state.getMainReservoirPressure(), 0.005)));

        if (state.getType() == LocomotiveType.TE33A) {
            params.put("oil_pressure", round(addNoise(state.getOilPressure(), 0.005)));
            params.put("boost_pressure", round(addNoise(state.getBoostPressure(), 5)));
        }

        // --- Fuel (ТЭ33А) ---
        if (state.getType() == LocomotiveType.TE33A) {
            params.put("fuel_level", round(state.getFuelLevel()));
            params.put("fuel_rate", round(addNoise(state.getFuelRate(), 2)));
            params.put("fuel_temp", round(addNoise(state.getFuelTemp(), 0.3)));
            params.put("engine_rpm", round(addNoise(state.getEngineRpm(), 3)));
        }

        // --- Electrics ---
        params.put("traction_motor_current", round(addNoise(state.getTractionMotorCurrent(), 5)));
        params.put("battery_voltage", round(addNoise(state.getBatteryVoltage(), 0.5)));

        if (state.getType() == LocomotiveType.KZ8A) {
            params.put("catenary_voltage", round(addNoise(state.getCatenaryVoltage(), 0.1)));
            params.put("dc_bus_voltage", round(addNoise(state.getDcBusVoltage(), 5)));
            params.put("regen_power", round(state.getRegenPower()));
        }

        // --- Aux ---
        params.put("sand_level", round(state.getSandLevel()));
        params.put("cabin_temp", round(addNoise(state.getCabinTemp(), 0.2)));

        // DTC codes
        List<String> dtcCodes = new ArrayList<>();
        if (state.getActiveAnomaly() != AnomalyType.NONE && state.getAnomalyIntensity() > 0.3) {
            dtcCodes.add(getDtcCode(state.getActiveAnomaly()));
        }

        return TelemetryMessage.builder()
            .locomotiveId(state.getLocomotiveId())
            .locomotiveType(state.getType())
            .timestamp(Instant.now())
            .phase(state.getPhase())
            .parameters(params)
            .activeDtcCodes(dtcCodes)
            .gpsLat(round(state.getGpsLat(), 6))
            .gpsLon(round(state.getGpsLon(), 6))
            .routeId(state.getRouteId())
            .odometer(round(state.getOdometer(), 2))
            .build();
    }

    // ═══════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════

    private double lerp(double current, double target, double factor) {
        return current + (target - current) * factor;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double addNoise(double value, double amplitude) {
        return value + RNG.nextDouble(-amplitude, amplitude);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private int randInt(int min, int max) {
        return RNG.nextInt(min, max + 1);
    }

    private double randDouble(double min, double max) {
        return RNG.nextDouble(min, max);
    }

    private int getMaxThrottle() {
        return state.getType() == LocomotiveType.KZ8A ? 32 : 8;
    }

    private String getDtcCode(AnomalyType anomaly) {
        return switch (anomaly) {
            case COOLANT_OVERHEAT       -> "DTC-T001";
            case OIL_PRESSURE_DROP      -> "DTC-P001";
            case BRAKE_PIPE_LEAK        -> "DTC-B001";
            case TRACTION_MOTOR_OVERHEAT-> "DTC-E001";
            case FUEL_LEAK              -> "DTC-F001";
            case CATENARY_VOLTAGE_SAG   -> "DTC-E002";
            case EXHAUST_TEMP_SPIKE     -> "DTC-T002";
            default                     -> "DTC-U000";
        };
    }
}
