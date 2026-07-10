package com.licenta.traffic_control.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final String lagQuery;
    private final String emqxDropQuery;

    public MetaController(
            @Value("${controller.prometheus.lag-query}") String lagQuery,
            @Value("${controller.prometheus.emqx-drop-query}") String emqxDropQuery) {
        this.lagQuery = lagQuery;
        this.emqxDropQuery = emqxDropQuery;
    }

    @GetMapping
    public Map<String, String> meta() {
        return Map.of(
                "lagQuery", lagQuery,
                "emqxDropQuery", emqxDropQuery);
    }
}
