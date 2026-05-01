package com.licenta.traffic_control;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TrafficControlApplication {

	static void main(String[] args) {
		SpringApplication.run(TrafficControlApplication.class, args);
	}

}
