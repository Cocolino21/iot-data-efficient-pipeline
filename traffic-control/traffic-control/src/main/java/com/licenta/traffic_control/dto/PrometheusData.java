package com.licenta.traffic_control.dto;

import java.util.List;

public record PrometheusData(String resultType, List<PrometheusResult> result) {}
