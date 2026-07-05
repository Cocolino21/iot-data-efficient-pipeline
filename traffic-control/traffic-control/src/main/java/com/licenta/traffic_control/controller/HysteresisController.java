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
            double overshoot = currentLag - settings.getUpperLag();
            output = settings.getStep() + settings.getGain() * overshoot;
        } else if (currentLag < settings.getLowerLag()) {
            double overshoot = settings.getLowerLag() - currentLag;
            output = -(settings.getStep() + settings.getGain() * overshoot);
        } else {
            return 0;
        }

        double clamped = Math.max(settings.getOutputMin(), Math.min(settings.getOutputMax(), output));
        log.debug("HYSTERESIS: lag={}, output={}, clamped={}",
                currentLag, String.format("%.2f", output), String.format("%.2f", clamped));
        return clamped;
    }
}
