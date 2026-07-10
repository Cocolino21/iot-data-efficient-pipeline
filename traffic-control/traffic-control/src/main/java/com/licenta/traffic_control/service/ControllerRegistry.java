package com.licenta.traffic_control.service;

import com.licenta.traffic_control.config.ControllerSettings;
import com.licenta.traffic_control.domain.enums.ControllerMode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ControllerRegistry {

    private final Map<ControllerMode, LagController> byMode;
    private final ControllerSettings settings;

    public ControllerRegistry(List<LagController> controllers, ControllerSettings settings) {
        this.byMode = controllers.stream()
                .collect(Collectors.toMap(LagController::mode, Function.identity()));
        this.settings = settings;
    }

    public LagController active() {
        return byMode.get(settings.getMode());
    }
}
