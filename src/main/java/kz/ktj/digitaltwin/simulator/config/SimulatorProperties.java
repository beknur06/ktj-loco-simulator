package kz.ktj.digitaltwin.simulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "simulator")
public class SimulatorProperties {

    private boolean enabled = true;
    private int tickIntervalMs = 1000;

    private List<LocomotiveConfig> locomotives = new ArrayList<>();

    private AnomalyConfig anomaly = new AnomalyConfig();

    @Data
    public static class LocomotiveConfig {
        private String id;
        private String type; // KZ8A or TE33A
        private String route;
    }

    @Data
    public static class AnomalyConfig {
        private double probability = 0.005;
        private int durationSeconds = 60;
    }
}
