package com.licenta.coreservice.controller;

import com.licenta.coreservice.dto.CreateDeviceRequest;
import com.licenta.coreservice.dto.DeviceDto;
import com.licenta.coreservice.security.CurrentUser;
import com.licenta.coreservice.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public List<DeviceDto> list(@AuthenticationPrincipal CurrentUser user) {
        return deviceService.list(user.id());
    }

    @GetMapping("/{id}")
    public DeviceDto get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return deviceService.get(id, user.id());
    }

    @PostMapping
    public DeviceDto create(@AuthenticationPrincipal CurrentUser user,
                            @Valid @RequestBody CreateDeviceRequest req) {
        return deviceService.create(req, user.id());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        deviceService.delete(id, user.id());
        return ResponseEntity.noContent().build();
    }
}
