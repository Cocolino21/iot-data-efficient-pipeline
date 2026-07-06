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
    private final String emqxDropQuery;

    public PrometheusClient(
            @Value("${controller.prometheus.url}") String baseUrl,
            @Value("${controller.prometheus.lag-query}") String lagQuery,
            @Value("${controller.prometheus.emqx-drop-query}") String emqxDropQuery) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.lagQuery = lagQuery;
        this.emqxDropQuery = emqxDropQuery;
    }

    public long getConsumerLag() {
        return queryScalar(lagQuery);
    }

    public double getEmqxDropRate() {
        return queryDouble(emqxDropQuery);
    }

    private long queryScalar(String query) {
        PrometheusResponse response = restClient.get()
                // Pass the query as a URI variable value so PromQL curly braces
                // (label filters) aren't parsed as URI template placeholders.
                .uri(uri -> uri.path("/api/v1/query").queryParam("query", "{q}").build(query))
                .retrieve()
                .body(PrometheusResponse.class);

        if (response == null || !"success".equals(response.status())) {
            log.warn("Prometheus query returned unsuccessful response: {}", response);
            return 0L;
        }
        List<PrometheusResult> results = response.data() != null ? response.data().result() : null;
        if (results == null || results.isEmpty()) {
            log.debug("No results for query '{}' — assuming 0", query);
            return 0L;
        }
        String valueStr = String.valueOf(results.getFirst().value().get(1));
        return (long) Double.parseDouble(valueStr);
    }

    private double queryDouble(String query) {
        PrometheusResponse response = restClient.get()
                // Pass the query as a URI variable value so PromQL curly braces
                // (label filters) aren't parsed as URI template placeholders.
                .uri(uri -> uri.path("/api/v1/query").queryParam("query", "{q}").build(query))
                .retrieve()
                .body(PrometheusResponse.class);

        if (response == null || !"success".equals(response.status())) {
            log.warn("Prometheus query returned unsuccessful response: {}", response);
            return 0.0;
        }
        List<PrometheusResult> results = response.data() != null ? response.data().result() : null;
        if (results == null || results.isEmpty()) {
            log.debug("No results for query '{}' — assuming 0", query);
            return 0.0;
        }
        String valueStr = String.valueOf(results.getFirst().value().get(1));
        return Double.parseDouble(valueStr);
    }
}
