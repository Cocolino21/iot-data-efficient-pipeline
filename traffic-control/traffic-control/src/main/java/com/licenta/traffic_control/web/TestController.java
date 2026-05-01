package com.licenta.traffic_control.web;

import com.licenta.traffic_control.actuator.MqttActuator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Temporary: lets us trigger MQTT publishes by hand while verifying Step 2.
// Removed in Step 3 once DecisionEngine takes over.
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final MqttActuator mqttActuator;

    @PostMapping("/pip")
    public String publishPip(@RequestParam double pct) {
        mqttActuator.publishPipPercentage(pct);
        return "published percentage=" + pct;
    }
}
