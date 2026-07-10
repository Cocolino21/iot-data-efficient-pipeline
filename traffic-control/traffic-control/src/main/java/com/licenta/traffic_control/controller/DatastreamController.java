package com.licenta.traffic_control.controller;

import com.licenta.traffic_control.dto.Observation;
import com.licenta.traffic_control.service.DatastreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datastreams")
@RequiredArgsConstructor
public class DatastreamController {

    private final DatastreamService datastreamService;

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String q) {
        return datastreamService.list(page, size, q);
    }

    @GetMapping("/{id}/baseline")
    public List<Map<String, Object>> baseline(@PathVariable String id) {
        return datastreamService.baseline(id);
    }

    @GetMapping("/{id}/raw")
    public List<Observation> raw(
            @PathVariable String id,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to) {
        return datastreamService.raw(id, from, to);
    }

    @GetMapping("/{id}/reconstructed")
    public Map<String, Object> reconstructed(
            @PathVariable String id,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to) {
        return datastreamService.reconstructed(id, from, to);
    }

    @GetMapping("/{id}/aggregates")
    public List<Map<String, Object>> aggregates(
            @PathVariable String id,
            @RequestParam(defaultValue = "hourly") String tier,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to) {
        return datastreamService.aggregates(id, tier, from, to);
    }
}
