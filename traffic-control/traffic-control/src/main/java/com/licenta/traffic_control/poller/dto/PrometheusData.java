package com.licenta.traffic_control.poller.dto;

import java.util.List;

public record PrometheusData(String resultType, List<PrometheusResult> result) {}
