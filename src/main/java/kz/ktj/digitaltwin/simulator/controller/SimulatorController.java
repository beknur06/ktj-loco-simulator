package kz.ktj.digitaltwin.simulator.controller;

import kz.ktj.digitaltwin.simulator.engine.LocomotiveState;
import kz.ktj.digitaltwin.simulator.service.SimulatorOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API для управления симулятором.
 * Позволяет менять параметры на лету — полезно для демо.
 */
@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {

    private final SimulatorOrchestrator orchestrator;

    public SimulatorController(SimulatorOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * GET /api/simulator/status
     * Статус симулятора: запущен ли, интервал тика, список локомотивов.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
            "running", orchestrator.isRunning(),
            "tickIntervalMs", orchestrator.getTickIntervalMs(),
            "locomotives", orchestrator.getLocomotiveIds()
        ));
    }

    /**
     * GET /api/simulator/locomotives
     * Список ID всех локомотивов.
     */
    @GetMapping("/locomotives")
    public ResponseEntity<List<String>> locomotives() {
        return ResponseEntity.ok(orchestrator.getLocomotiveIds());
    }

    /**
     * GET /api/simulator/locomotives/{id}/state
     * Текущее состояние конкретного локомотива (для отладки).
     */
    @GetMapping("/locomotives/{id}/state")
    public ResponseEntity<LocomotiveState> locomotiveState(@PathVariable String id) {
        LocomotiveState state = orchestrator.getState(id);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(state);
    }

    /**
     * POST /api/simulator/start
     * Запустить симулятор.
     */
    @PostMapping("/start")
    public ResponseEntity<String> start() {
        orchestrator.start();
        return ResponseEntity.ok("Simulator started");
    }

    /**
     * POST /api/simulator/stop
     * Остановить симулятор.
     */
    @PostMapping("/stop")
    public ResponseEntity<String> stop() {
        orchestrator.stop();
        return ResponseEntity.ok("Simulator stopped");
    }

    /**
     * POST /api/simulator/tick-interval?ms=100
     * Изменить интервал тика.
     * ms=1000 → 1 Гц (нормальный режим)
     * ms=100  → 10 Гц (x10 highload)
     * ms=50   → 20 Гц (стресс-тест)
     */
    @PostMapping("/tick-interval")
    public ResponseEntity<String> setTickInterval(@RequestParam int ms) {
        if (ms < 50 || ms > 10000) {
            return ResponseEntity.badRequest().body("Interval must be 50-10000 ms");
        }
        orchestrator.setTickInterval(ms);
        return ResponseEntity.ok("Tick interval set to " + ms + "ms (" + (1000.0 / ms) + " Hz)");
    }

    /**
     * POST /api/simulator/reset
     * Сбросить все состояния локомотивов к начальным значениям.
     */
    @PostMapping("/reset")
    public ResponseEntity<String> reset() {
        orchestrator.reset();
        return ResponseEntity.ok("Simulator reset: all locomotive states reinitialized");
    }

    /**
     * POST /api/simulator/highload
     * Быстрое включение режима высокой нагрузки (x10).
     */
    @PostMapping("/highload")
    public ResponseEntity<String> highload(@RequestParam(defaultValue = "true") boolean enabled) {
        int ms = enabled ? 100 : 1000;
        orchestrator.setTickInterval(ms);
        return ResponseEntity.ok("Highload mode " + (enabled ? "ON (10 Hz)" : "OFF (1 Hz)"));
    }
}
