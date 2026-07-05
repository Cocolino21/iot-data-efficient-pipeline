package com.licenta.coreservice.sensor;

import com.licenta.coreservice.auth.CurrentUser;
import com.licenta.coreservice.sensor.dto.CreateSensorRequest;
import com.licenta.coreservice.sensor.dto.SensorDto;
import com.licenta.coreservice.sensor.dto.UpdateDatastreamRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class SensorController {

    private final SensorService sensorService;

    public SensorController(SensorService sensorService) {
        this.sensorService = sensorService;
    }

    @PostMapping("/api/devices/{deviceId}/sensors")
    public SensorDto create(@AuthenticationPrincipal CurrentUser user,
                            @PathVariable UUID deviceId,
                            @Valid @RequestBody CreateSensorRequest req) {
        return sensorService.create(deviceId, req, user.id());
    }

    @PatchMapping("/api/datastreams/{id}")
    public ResponseEntity<Void> patchDatastream(@AuthenticationPrincipal CurrentUser user,
                                                @PathVariable UUID id,
                                                @RequestBody UpdateDatastreamRequest req) {
        if (req.is_active() != null) {
            sensorService.updateActive(id, req.is_active(), user.id());
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/sensors/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        sensorService.delete(id, user.id());
        return ResponseEntity.noContent().build();
    }
}
