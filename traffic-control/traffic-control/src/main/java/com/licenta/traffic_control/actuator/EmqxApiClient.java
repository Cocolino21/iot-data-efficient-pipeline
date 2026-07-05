package com.licenta.traffic_control.actuator;

import com.licenta.traffic_control.config.EmqxTuningSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class EmqxApiClient {

    private final EmqxTuningSettings settings;

    public EmqxApiClient(EmqxTuningSettings settings) {
        this.settings = settings;
    }

    public void pushUpperLimits() {
        updateBridgeParams(
                settings.getUpperMaxLingerTime(),
                settings.getUpperMaxLingerBytes(),
                settings.getUpperMaxBatchBytes(),
                settings.getUpperMaxInflight()
        );
    }

    public void restoreDefaults() {
        updateBridgeParams(
                settings.getDefaultMaxLingerTime(),
                settings.getDefaultMaxLingerBytes(),
                settings.getDefaultMaxBatchBytes(),
                settings.getDefaultMaxInflight()
        );
    }

    private void updateBridgeParams(String lingerTime, String lingerBytes, String batchBytes, int inflight) {
        String url = settings.getApiUrl() + "/api/v5/actions/" + settings.getActionId();
        String auth = Base64.getEncoder().encodeToString(
                (settings.getApiUser() + ":" + settings.getApiPassword()).getBytes()
        );

        Map<String, Object> parameters = Map.of(
                "max_linger_time", lingerTime,
                "max_linger_bytes", lingerBytes,
                "max_batch_bytes", batchBytes,
                "max_inflight", inflight
        );
        Map<String, Object> body = Map.of("parameters", parameters);

        try {
            RestClient.create()
                    .put()
                    .uri(url)
                    .header("Authorization", "Basic " + auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("EMQX bridge updated: linger_time={}, linger_bytes={}, batch_bytes={}, inflight={}",
                    lingerTime, lingerBytes, batchBytes, inflight);
        } catch (Exception e) {
            log.error("Failed to update EMQX bridge params: {}", e.getMessage());
        }
    }
}
