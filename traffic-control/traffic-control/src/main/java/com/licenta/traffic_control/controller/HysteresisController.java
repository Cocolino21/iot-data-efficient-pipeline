package com.licenta.traffic_control.controller;

import com.licenta.traffic_control.config.HysteresisSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
@Slf4j
@Component
@RequiredArgsConstructor
public class HysteresisController implements LagController {

    private final HysteresisSettings settings;

    @Override
    public ControllerMode mode() {
        return ControllerMode.HYSTERESIS;
    }

    @Override
    public double compute(long currentLag) {
        double output;
        if (currentLag > settings.getUpperLag()) {
            // Shed scales with severity: relative overshoot past the edge, so
            // the same gain works for any band placement (lag at 2x the edge
            // adds `gain` percent on top of the base step).
            double upper = Math.max(1, settings.getUpperLag());
            double overshoot = (currentLag - upper) / upper;
            output = settings.getStep() + settings.getGain() * overshoot;
        } else if (currentLag < settings.getLowerLag()) {
            // Relax is a flat gentle decay: the actuator is cumulative, so an
            // overshoot-scaled relax would flood traffic back and limit-cycle.
            output = -settings.getRelaxStep();
        } else {
            return 0;
        }

        double clamped = Math.max(settings.getOutputMin(), Math.min(settings.getOutputMax(), output));
        log.debug("HYSTERESIS: lag={}, output={}, clamped={}",
                currentLag, String.format("%.2f", output), String.format("%.2f", clamped));
        return clamped;
    }
}
