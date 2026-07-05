package com.licenta.coreservice.observation;

import com.licenta.coreservice.auth.CurrentUser;
import com.licenta.coreservice.observation.dto.ObservationDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ObservationController {

    private final ObservationService observationService;

    public ObservationController(ObservationService observationService) {
        this.observationService = observationService;
    }

    @GetMapping("/api/datastreams/{id}/observations")
    public List<ObservationDto> read(@AuthenticationPrincipal CurrentUser user,
                                     @PathVariable UUID id,
                                     @RequestParam long from,
                                     @RequestParam long to,
                                     @RequestParam(defaultValue = "500") int maxPoints) {
        return observationService.read(id, from, to, maxPoints, user.id());
    }
}
