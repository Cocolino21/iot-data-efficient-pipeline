package com.licenta.coreservice.device;

import com.licenta.coreservice.common.NotFoundException;
import com.licenta.coreservice.device.dto.CreateDeviceRequest;
import com.licenta.coreservice.device.dto.DeviceDto;
import com.licenta.coreservice.sensor.SensorRepository;
import com.licenta.coreservice.sensor.dto.SensorDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DeviceService {

    private static final long ONLINE_WINDOW_MS = 5 * 60 * 1000L;

    private final DeviceRepository deviceRepo;
    private final SensorRepository sensorRepo;

    public DeviceService(DeviceRepository deviceRepo, SensorRepository sensorRepo) {
        this.deviceRepo = deviceRepo;
        this.sensorRepo = sensorRepo;
    }

    public List<DeviceDto> list(UUID userId) {
        List<DeviceRepository.DeviceRow> rows = deviceRepo.listByOwner(userId);
        Map<UUID, List<SensorDto>> sensorsByDevice = sensorsGrouped(rows.stream().map(DeviceRepository.DeviceRow::uuid).toList());
        return rows.stream().map(r -> toDto(r, sensorsByDevice.getOrDefault(r.uuid(), List.of()))).toList();
    }

    public DeviceDto get(UUID id, UUID userId) {
        DeviceRepository.DeviceRow row = deviceRepo.findByIdForOwner(id, userId)
                .orElseThrow(() -> new NotFoundException("device"));
        List<SensorDto> sensors = sensorsGrouped(List.of(id)).getOrDefault(id, List.of());
        return toDto(row, sensors);
    }

    @Transactional
    public DeviceDto create(CreateDeviceRequest req, UUID userId) {
        UUID id = deviceRepo.insert(userId, req.name(), req.description(), req.latitude(), req.longitude());
        return new DeviceDto(id, req.name(), req.description(), req.latitude(), req.longitude(),
                "offline", null, List.of());
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        DeviceRepository.DeviceRow row = deviceRepo.findByIdForOwner(id, userId)
                .orElseThrow(() -> new NotFoundException("device"));
        sensorRepo.deleteDatastreamsForThing(row.uuid());
        deviceRepo.deleteForOwner(id, userId);
    }

    private Map<UUID, List<SensorDto>> sensorsGrouped(List<UUID> deviceIds) {
        Map<UUID, List<SensorDto>> map = new HashMap<>();
        sensorRepo.findForDevices(deviceIds).forEach(r ->
                map.computeIfAbsent(r.deviceId(), k -> new java.util.ArrayList<>()).add(r.sensor()));
        return map;
    }

    private DeviceDto toDto(DeviceRepository.DeviceRow r, List<SensorDto> sensors) {
        String status = (r.lastSeenAtMs() != null && Instant.now().toEpochMilli() - r.lastSeenAtMs() < ONLINE_WINDOW_MS)
                ? "online" : "offline";
        return new DeviceDto(r.uuid(), r.name(), r.description(), r.latitude(), r.longitude(),
                status, r.lastSeenAtMs(), sensors);
    }
}
