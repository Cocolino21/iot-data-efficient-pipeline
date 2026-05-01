package com.licenta.traffic_control.poller.dto;

import java.util.List;
import java.util.Map;

public record PrometheusResult(Map<String, String> metric, List<Object> value) {}
