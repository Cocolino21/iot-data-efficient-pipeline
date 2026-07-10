package com.licenta.coreservice.service;

import com.licenta.coreservice.dto.CreateSensorRequest;
import com.licenta.coreservice.dto.SensorDto;
import com.licenta.coreservice.exception.NotFoundException;
import com.licenta.coreservice.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SensorService {

    private final SensorRepository repo;

    @Transactional
    public SensorDto create(UUID deviceId, CreateSensorRequest req, UUID userId) {
        UUID owner = repo.findOwnerOfDevice(deviceId).orElseThrow(() -> new NotFoundException("device"));
        if (!owner.equals(userId)) throw new NotFoundException("device");

        String externalId = (req.externalId() != null && !req.externalId().isBlank())
                ? req.externalId()
                : req.type() + "-" + shortId();
        Map<String, Object> meta = Map.of(
                "type", req.type(),
                "external_id", externalId);
        UUID sensorUuid = repo.insertSensor(req.name(), null, meta);
        String symbol = req.symbol() != null && !req.symbol().isBlank() ? req.symbol() : req.unit();
        UUID opUuid = repo.insertObservedProperty(req.observed(), null, req.unit(), symbol);
        UUID dsUuid = repo.insertDatastream(deviceId, sensorUuid, opUuid, req.name(), req.type(), true, externalId);

        return new SensorDto(dsUuid, req.name(), req.type(), req.observed(), req.unit(), symbol, true, externalId);
    }

    @Transactional
    public void updateActive(UUID datastreamId, boolean isActive, UUID userId) {
        UUID owner = repo.findOwnerOfDatastream(datastreamId).orElseThrow(() -> new NotFoundException("datastream"));
        if (!owner.equals(userId)) throw new NotFoundException("datastream");
        repo.updateDatastreamActive(datastreamId, isActive);
    }

    @Transactional
    public void delete(UUID datastreamId, UUID userId) {
        SensorRepository.DatastreamRefs refs = repo.findRefsByDatastreamId(datastreamId)
                .orElseThrow(() -> new NotFoundException("sensor"));
        UUID owner = repo.findOwnerOfDevice(refs.thingId()).orElse(null);
        if (owner == null || !owner.equals(userId)) throw new NotFoundException("sensor");

        repo.deleteDatastream(datastreamId);
        repo.deleteSensor(refs.sensorId());
        repo.deleteObservedProperty(refs.observedPropertyId());
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
