package com.licenta.coreservice.service;

import com.licenta.coreservice.dto.ObservationDto;
import com.licenta.coreservice.exception.NotFoundException;
import com.licenta.coreservice.repository.ObservationRepository;
import com.licenta.coreservice.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ObservationService {

    private final SensorRepository sensorRepo;
    private final ObservationRepository observationRepo;

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
