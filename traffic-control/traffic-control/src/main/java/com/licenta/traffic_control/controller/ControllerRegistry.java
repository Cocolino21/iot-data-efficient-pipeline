package com.licenta.traffic_control.controller;

import com.licenta.traffic_control.config.ControllerSettings;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Indexes the available {@link LagController} strategies by their mode and hands
 * back the one selected by {@code controller.mode}. Returns {@code null} for
 * {@link ControllerMode#NONE}, meaning no broadcasting.
 */
@Component
public class ControllerRegistry {

    private final Map<ControllerMode, LagController> byMode;
    private final ControllerSettings settings;

    public ControllerRegistry(List<LagController> controllers, ControllerSettings settings) {
        this.byMode = controllers.stream()
                .collect(Collectors.toMap(LagController::mode, Function.identity()));
        this.settings = settings;
    }

    /** The controller for the current mode, or {@code null} when mode is NONE. */
    public LagController active() {
        return byMode.get(settings.getMode());
    }
}
