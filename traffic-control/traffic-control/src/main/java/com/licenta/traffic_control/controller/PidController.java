package com.licenta.traffic_control.controller;

import com.licenta.traffic_control.config.PidSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PidController implements LagController {

    private final PidSettings settings;

    private double integral = 0;
    private double previousError = 0;
    private long lastTimeNanos = 0;

    @Override
    public ControllerMode mode() {
        return ControllerMode.PID;
    }

    @Override
    public double compute(long currentLag) {
        long now = System.nanoTime();
        double dt = lastTimeNanos == 0 ? 0 : (now - lastTimeNanos) / 1_000_000_000.0;
        lastTimeNanos = now;

        if (dt <= 0) {
            previousError = currentLag - settings.getTargetLag();
            return 0;
        }

        double error = currentLag - settings.getTargetLag();

        // Integral with windup protection
        integral += error * dt;
        integral = Math.max(-settings.getIntegralMax(), Math.min(settings.getIntegralMax(), integral));

        // Derivative
        double derivative = (error - previousError) / dt;
        previousError = error;

        double output = settings.getKp() * error + settings.getKi() * integral + settings.getKd() * derivative;
        double clamped = Math.max(settings.getOutputMin(), Math.min(settings.getOutputMax(), output));

        log.debug("PID: error={}, integral={}, derivative={}, output={}, clamped={}",
                String.format("%.1f", error),
                String.format("%.1f", integral),
                String.format("%.1f", derivative),
                String.format("%.2f", output),
                String.format("%.2f", clamped));

        return clamped;
    }

    @Override
    public void reset() {
        integral = 0;
        previousError = 0;
        lastTimeNanos = 0;
    }
}
