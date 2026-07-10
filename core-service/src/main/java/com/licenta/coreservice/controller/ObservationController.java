package com.licenta.coreservice.controller;

import com.licenta.coreservice.dto.ObservationDto;
import com.licenta.coreservice.security.CurrentUser;
import com.licenta.coreservice.service.ObservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ObservationController {

    private final ObservationService observationService;

    @GetMapping("/api/datastreams/{id}/observations")
    public List<ObservationDto> read(@AuthenticationPrincipal CurrentUser user,
                                     @PathVariable UUID id,
                                     @RequestParam long from,
                                     @RequestParam long to,
                                     @RequestParam(defaultValue = "500") int maxPoints) {
        return observationService.read(id, from, to, maxPoints, user.id());
    }
}
