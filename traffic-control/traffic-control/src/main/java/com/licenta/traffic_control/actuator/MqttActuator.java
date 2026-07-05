package com.licenta.traffic_control.actuator;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class MqttActuator {

    private final String broker;
    private final String clientId;
    private final String broadcastTopic;

    private MqttClient client;

    public MqttActuator(
            @Value("${controller.mqtt.broker}") String broker,
            @Value("${controller.mqtt.client-id}") String clientId,
            @Value("${controller.mqtt.broadcast-topic}") String broadcastTopic) {
        this.broker = broker;
        this.clientId = clientId;
        this.broadcastTopic = broadcastTopic;
    }

    @PostConstruct
    public void connect() throws MqttException {
        client = new MqttClient(broker, clientId, new MemoryPersistence());
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setAutomaticReconnect(true);
        client.connect(opts);
        log.info("MQTT connected to {} as {}", broker, clientId);
    }

    @PreDestroy
    public void disconnect() throws MqttException {
        if (client != null && client.isConnected()) {
            client.disconnect();
            client.close();
            log.info("MQTT disconnected");
        }
    }

    public void publishPipPercentage(double pct) {
        String payload = "{\"percentage\":" + pct + "}";
        publish(broadcastTopic, payload);
    }

    public void publishRawMode(String thingId, String datastreamId, int ttlSeconds) {
        String topic = "cmd/control/" + thingId;
        String payload = "{\"mode\":\"raw\",\"datastream_id\":\"" + datastreamId + "\",\"ttl_s\":" + ttlSeconds + "}";
        publish(topic, payload);
    }

    private void publish(String topic, String payload) {
        MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        msg.setQos(2);
        try {
            client.publish(topic, msg);
            log.info("published to {}: {}", topic, payload);
        } catch (MqttException e) {
            log.error("MQTT publish to {} failed: {}", topic, e.getMessage());
        }
    }
}
