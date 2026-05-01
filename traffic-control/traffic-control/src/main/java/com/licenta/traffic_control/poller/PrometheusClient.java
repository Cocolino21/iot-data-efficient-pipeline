package com.licenta.traffic_control.poller;

import com.licenta.traffic_control.poller.dto.PrometheusResponse;
import com.licenta.traffic_control.poller.dto.PrometheusResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class PrometheusClient {

    private final RestClient restClient;
    private final String lagQuery;

    public PrometheusClient(
            @Value("${controller.prometheus.url}") String baseUrl,
            @Value("${controller.prometheus.lag-query}") String lagQuery) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.lagQuery = lagQuery;
    }

    public long getConsumerLag() {
        PrometheusResponse response = restClient.get()
                .uri(uri -> uri.path("/api/v1/query").queryParam("query", lagQuery).build())
                .retrieve()
                .body(PrometheusResponse.class);

        if (response == null || !"success".equals(response.status())) {
            log.warn("Prometheus query returned unsuccessful response: {}", response);
            return 0L;
        }
        List<PrometheusResult> results = response.data() != null ? response.data().result() : null;
        if (results == null || results.isEmpty()) {
            log.debug("No results for lag query — assuming 0 lag");
            return 0L;
        }
        String valueStr = String.valueOf(results.getFirst().value().get(1));
        return (long) Double.parseDouble(valueStr);
    }
}
