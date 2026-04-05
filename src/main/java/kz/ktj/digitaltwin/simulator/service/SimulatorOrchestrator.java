package kz.ktj.digitaltwin.simulator.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import kz.ktj.digitaltwin.simulator.config.SimulatorProperties;
import kz.ktj.digitaltwin.simulator.engine.LocomotiveState;
import kz.ktj.digitaltwin.simulator.engine.SimulationEngine;
import kz.ktj.digitaltwin.simulator.model.LocomotiveType;
import kz.ktj.digitaltwin.simulator.model.TelemetryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class SimulatorOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SimulatorOrchestrator.class);

    private final SimulatorProperties properties;
    private final TelemetryPublisher publisher;

    private final List<SimulationEngine> engines = new ArrayList<>();
    private final Map<String, LocomotiveState> states = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    private volatile int tickIntervalMs;
    private volatile boolean running = false;

    public SimulatorOrchestrator(SimulatorProperties properties, TelemetryPublisher publisher) {
        this.properties = properties;
        this.publisher = publisher;
        this.tickIntervalMs = properties.getTickIntervalMs();
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("Simulator is disabled");
            return;
        }

        for (SimulatorProperties.LocomotiveConfig config : properties.getLocomotives()) {
            LocomotiveState state = new LocomotiveState();
            state.setLocomotiveId(config.getId());
            state.setType(LocomotiveType.valueOf(config.getType()));
            state.setRouteId(config.getRoute());
            state.setAmbientTemp(ThreadLocalRandom.current().nextDouble(-10, 30));

            SimulationEngine engine = new SimulationEngine(state, properties.getAnomaly().getProbability());
            engines.add(engine);
            states.put(config.getId(), state);

            log.info("Registered locomotive: {} ({}) on route {}", config.getId(), config.getType(), config.getRoute());
        }

        log.info("Simulator initialized. Awaiting manual start via API.");
    }

    public void start() {
        if (running) return;
        running = true;

        scheduler = Executors.newScheduledThreadPool(engines.size());

        for (SimulationEngine engine : engines) {
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    TelemetryMessage msg = engine.tick();
                    publisher.publish(msg);
                } catch (Exception e) {
                    log.error("Tick error: {}", e.getMessage(), e);
                }
            }, 0, tickIntervalMs, TimeUnit.MILLISECONDS);
        }

        log.info("Simulator started: {} locomotives, tick={}ms", engines.size(), tickIntervalMs);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
        log.info("Simulator stopped");
    }

    public void setTickInterval(int ms) {
        this.tickIntervalMs = ms;
        stop();
        start();
        log.info("Tick interval changed to {}ms", ms);
    }

    public LocomotiveState getState(String locomotiveId) {
        return states.get(locomotiveId);
    }

    public List<String> getLocomotiveIds() {
        return new ArrayList<>(states.keySet());
    }

    public boolean isRunning() {
        return running;
    }

    public int getTickIntervalMs() {
        return tickIntervalMs;
    }
}
