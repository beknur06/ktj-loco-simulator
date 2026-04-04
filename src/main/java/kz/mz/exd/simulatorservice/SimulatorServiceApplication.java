package kz.mz.exd.simulatorservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SimulatorServiceApplication {

	public static void main(String[] args) {
        System.out.println("Starting Simulator Service Application...");
		SpringApplication.run(SimulatorServiceApplication.class, args);
	}

}
