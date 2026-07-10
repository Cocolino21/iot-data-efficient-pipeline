package com.licenta.traffic_control.controller;

import com.licenta.traffic_control.client.MqttActuator;
import com.licenta.traffic_control.config.CalibrationSettings;
import com.licenta.traffic_control.config.ControllerSettings;
import com.licenta.traffic_control.config.EmqxTuningSettings;
import com.licenta.traffic_control.config.HysteresisSettings;
import com.licenta.traffic_control.config.PidSettings;
import com.licenta.traffic_control.dto.ControllerUpdate;
import com.licenta.traffic_control.scheduler.CalibrationOrchestrator;
import com.licenta.traffic_control.scheduler.EmqxDropsPoller;
import com.licenta.traffic_control.service.ControllerRegistry;
import com.licenta.traffic_control.service.LagController;
import com.licenta.traffic_control.service.PidController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettingsController {

    private final PidSettings pidSettings;
    private final HysteresisSettings hysteresisSettings;
    private final ControllerSettings controllerSettings;
    private final ControllerRegistry controllerRegistry;
    private final CalibrationSettings calibrationSettings;
    private final EmqxTuningSettings emqxSettings;
    private final PidController pidController;
    private final MqttActuator mqttActuator;
    private final EmqxDropsPoller emqxDropsPoller;
    private final CalibrationOrchestrator calibrationOrchestrator;

    @GetMapping("/controller")
    public ControllerSettings getControllerSettings() {
        return controllerSettings;
    }

    @PutMapping("/controller")
    public ControllerSettings updateControllerSettings(@RequestBody ControllerUpdate update) {
        if (update.mode() != null) {
            controllerSettings.setMode(update.mode());
            LagController active = controllerRegistry.active();
            if (active != null) active.reset();
        }
        if (update.deadZone() != null) controllerSettings.setDeadZone(update.deadZone());
        if (update.pollIntervalMs() != null) {
            controllerSettings.setPollIntervalMs(Math.max(1000, update.pollIntervalMs()));
        }
        return controllerSettings;
    }

    @GetMapping("/pid")
    public PidSettings getPidSettings() {
        return pidSettings;
    }

    @PutMapping("/pid")
    public PidSettings updatePidSettings(@RequestBody PidSettings update) {
        pidSettings.setKp(update.getKp());
        pidSettings.setKi(update.getKi());
        pidSettings.setKd(update.getKd());
        pidSettings.setTargetLag(update.getTargetLag());
        pidSettings.setIntegralMax(update.getIntegralMax());
        pidSettings.setOutputMin(update.getOutputMin());
        pidSettings.setOutputMax(update.getOutputMax());
        pidController.reset();
        return pidSettings;
    }

    @GetMapping("/hysteresis")
    public HysteresisSettings getHysteresisSettings() {
        return hysteresisSettings;
    }

    @PutMapping("/hysteresis")
    public HysteresisSettings updateHysteresisSettings(@RequestBody HysteresisSettings update) {
        hysteresisSettings.setUpperLag(update.getUpperLag());
        hysteresisSettings.setLowerLag(update.getLowerLag());
        hysteresisSettings.setStep(update.getStep());
        hysteresisSettings.setGain(update.getGain());
        hysteresisSettings.setRelaxStep(update.getRelaxStep());
        hysteresisSettings.setOutputMin(update.getOutputMin());
        hysteresisSettings.setOutputMax(update.getOutputMax());
        return hysteresisSettings;
    }

    @GetMapping("/calibration/settings")
    public CalibrationSettings getCalibrationSettings() {
        return calibrationSettings;
    }

    @PutMapping("/calibration/settings")
    public CalibrationSettings updateCalibrationSettings(@RequestBody CalibrationSettings update) {
        if (update.getMaxConcurrent() > 0) calibrationSettings.setMaxConcurrent(update.getMaxConcurrent());
        if (update.getCollectionTtlSeconds() > 0) calibrationSettings.setCollectionTtlSeconds(update.getCollectionTtlSeconds());
        if (update.getBaselineDays() > 0) calibrationSettings.setBaselineDays(update.getBaselineDays());
        if (update.getPollIntervalMs() > 0) calibrationSettings.setPollIntervalMs(update.getPollIntervalMs());
        if (update.getDriftThreshold() > 0) calibrationSettings.setDriftThreshold(update.getDriftThreshold());
        if (update.getMinHours() > 0) calibrationSettings.setMinHours(update.getMinHours());
        return calibrationSettings;
    }

    @PostMapping("/calibration/trigger")
    public Map<String, String> triggerCalibration(@RequestParam String datastreamId) {
        return Map.of("result", calibrationOrchestrator.triggerNow(datastreamId));
    }

    @GetMapping("/calibration/state")
    public Map<String, Object> getCalibrationState(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String q) {
        return calibrationOrchestrator.getState(page, size, q);
    }

    @GetMapping("/emqx/settings")
    public EmqxTuningSettings getEmqxSettings() {
        return emqxSettings;
    }

    @PutMapping("/emqx/settings")
    public EmqxTuningSettings updateEmqxSettings(@RequestBody EmqxTuningSettings update) {
        emqxSettings.setEnabled(update.isEnabled());
        if (update.getDropRateThreshold() > 0) emqxSettings.setDropRateThreshold(update.getDropRateThreshold());
        if (update.getCooldownPolls() > 0) emqxSettings.setCooldownPolls(update.getCooldownPolls());
        if (update.getDefaultMaxLingerTime() != null) emqxSettings.setDefaultMaxLingerTime(update.getDefaultMaxLingerTime());
        if (update.getDefaultMaxLingerBytes() != null) emqxSettings.setDefaultMaxLingerBytes(update.getDefaultMaxLingerBytes());
        if (update.getDefaultMaxBatchBytes() != null) emqxSettings.setDefaultMaxBatchBytes(update.getDefaultMaxBatchBytes());
        if (update.getDefaultMaxInflight() > 0) emqxSettings.setDefaultMaxInflight(update.getDefaultMaxInflight());
        if (update.getUpperMaxLingerTime() != null) emqxSettings.setUpperMaxLingerTime(update.getUpperMaxLingerTime());
        if (update.getUpperMaxLingerBytes() != null) emqxSettings.setUpperMaxLingerBytes(update.getUpperMaxLingerBytes());
        if (update.getUpperMaxBatchBytes() != null) emqxSettings.setUpperMaxBatchBytes(update.getUpperMaxBatchBytes());
        if (update.getUpperMaxInflight() > 0) emqxSettings.setUpperMaxInflight(update.getUpperMaxInflight());
        return emqxSettings;
    }

    @GetMapping("/emqx/state")
    public Map<String, Object> getEmqxState() {
        return Map.of(
                "state", emqxDropsPoller.getState().name(),
                "lastDropRate", emqxDropsPoller.getLastDropRate(),
                "enabled", emqxSettings.isEnabled()
        );
    }

    @PostMapping("/test/pip")
    public String publishPip(@RequestParam double pct) {
        mqttActuator.publishPipPercentage(pct);
        return "published percentage=" + pct;
    }
}
