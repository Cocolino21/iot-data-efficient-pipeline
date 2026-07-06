package com.licenta.coreservice.observation;

import com.licenta.coreservice.common.NotFoundException;
import com.licenta.coreservice.observation.dto.ObservationDto;
import com.licenta.coreservice.sensor.SensorRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ObservationService {

    private final SensorRepository sensorRepo;
    private final ObservationRepository observationRepo;

    public ObservationService(SensorRepository sensorRepo, ObservationRepository observationRepo) {
        this.sensorRepo = sensorRepo;
        this.observationRepo = observationRepo;
    }

    public List<ObservationDto> read(UUID datastreamId, long fromMs, long toMs, int maxPoints, UUID userId) {
        SensorRepository.DatastreamRefs refs = sensorRepo.findRefsByDatastreamId(datastreamId)
                .orElseThrow(() -> new NotFoundException("datastream"));
        UUID owner = sensorRepo.findOwnerOfDevice(refs.thingId()).orElse(null);
        if (owner == null || !owner.equals(userId)) throw new NotFoundException("datastream");

        String externalId = refs.externalId();
        if (externalId == null || externalId.isBlank()) return List.of();
        return observationRepo.readAdaptive(externalId, fromMs, toMs, Math.max(1, maxPoints));
    }
}
