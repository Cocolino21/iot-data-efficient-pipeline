package com.licenta.traffic_control.web;

import com.licenta.traffic_control.actuator.MqttActuator;
import com.licenta.traffic_control.calibration.CalibrationOrchestrator;
import com.licenta.traffic_control.config.CalibrationSettings;
import com.licenta.traffic_control.config.ControllerSettings;
import com.licenta.traffic_control.config.EmqxTuningSettings;
import com.licenta.traffic_control.config.HysteresisSettings;
import com.licenta.traffic_control.config.PidSettings;
import com.licenta.traffic_control.controller.ControllerRegistry;
import com.licenta.traffic_control.controller.LagController;
import com.licenta.traffic_control.controller.PidController;
import com.licenta.traffic_control.poller.EmqxDropsPoller;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    private final JdbcTemplate jdbc;
    private final CalibrationOrchestrator calibrationOrchestrator;

    // ── Controller mode ──────────────────────────────────

    @GetMapping("/controller")
    public ControllerSettings getControllerSettings() {
        return controllerSettings;
    }

    @PutMapping("/controller")
    public ControllerSettings updateControllerSettings(@RequestBody ControllerUpdate update) {
        // Nullable fields: only touch what was actually sent.
        if (update.mode() != null) {
            controllerSettings.setMode(update.mode());
            // Start the newly-activated controller from a clean slate (only on switch).
            LagController active = controllerRegistry.active();
            if (active != null) active.reset();
        }
        if (update.deadZone() != null) controllerSettings.setDeadZone(update.deadZone());
        if (update.pollIntervalMs() != null) {
            controllerSettings.setPollIntervalMs(Math.max(1000, update.pollIntervalMs()));
        }
        return controllerSettings;
    }

    // ── PID ──────────────────────────────────────────────

    @GetMapping("/pid")
    public PidSettings getPidSettings() {
        return pidSettings;
    }

    @PutMapping("/pid")
    public PidSettings updatePidSettings(@RequestBody PidSettings update) {
        // Full replace: the UI always submits every field, so 0 is a valid value
        // (e.g. Ki=0 for a PD controller), not a "field omitted" sentinel.
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

    // ── Hysteresis ───────────────────────────────────────

    @GetMapping("/hysteresis")
    public HysteresisSettings getHysteresisSettings() {
        return hysteresisSettings;
    }

    @PutMapping("/hysteresis")
    public HysteresisSettings updateHysteresisSettings(@RequestBody HysteresisSettings update) {
        // Full replace: every field comes from the UI form, so 0 is a real value.
        hysteresisSettings.setUpperLag(update.getUpperLag());
        hysteresisSettings.setLowerLag(update.getLowerLag());
        hysteresisSettings.setStep(update.getStep());
        hysteresisSettings.setGain(update.getGain());
        hysteresisSettings.setRelaxStep(update.getRelaxStep());
        hysteresisSettings.setOutputMin(update.getOutputMin());
        hysteresisSettings.setOutputMax(update.getOutputMax());
        return hysteresisSettings;
    }

    // ── Calibration ─────────────────────────────────────

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
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        String like = "%" + q.trim() + "%";
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT datastream_id, thing_id, status, needs_calibration,
                       drift_score, flagged_at, lease_started_at, lease_expires_at, last_collected_at
                FROM calibration_state
                WHERE datastream_id ILIKE ? OR thing_id ILIKE ?
                ORDER BY drift_score DESC, datastream_id
                LIMIT ? OFFSET ?
                """, like, like, safeSize, safePage * safeSize);
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM calibration_state WHERE datastream_id ILIKE ? OR thing_id ILIKE ?",
                Long.class, like, like);
        return Map.of("items", items, "total", total, "page", safePage, "size", safeSize);
    }

    // ── EMQX Tuning ───────────────────────────────────────

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

    // ── Manual trigger ──────────────────────────────────

    @PostMapping("/test/pip")
    public String publishPip(@RequestParam double pct) {
        mqttActuator.publishPipPercentage(pct);
        return "published percentage=" + pct;
    }
}

