# Adaptive Pipeline Controller — Plan

## Context

The pipeline has two demonstrated bottlenecks under sustained load growth:

1. **EMQX → Kafka** — producer replayq overflows, dropping messages (`replayq_overflow_dropped_produce_calls`).
2. **Kafka → sink-service → TimescaleDB** — consumer lag accumulates if Postgres COPY can't keep up with Kafka ingest rate.

Today, every "lever" (EMQX `max_inflight`, edge PIP threshold) is set manually. As device count and per-device publish rate grow over time, the operator has to predict load and pre-tune. This service automates that loop: poll Prometheus, decide which lever to pull, apply it, then slowly relax when load subsides.

Goal: a small Java Spring Boot service that keeps the pipeline lossless under variable load with minimal operator intervention. Designed for the thesis demo — favor clarity over generality.

**Scoping note:** Capacity provisioning (Kafka partition count, sink-service replicas) is operator-controlled, *not* automated by this service. The controller only tunes runtime parameters and sheds load at the source — it does not provision infrastructure.

---

## Architecture

```
                  ┌──────────────────────┐
                  │ Prometheus (existing) │
                  └──────────┬────────────┘
                             │ HTTP /api/v1/query
                             ▼
        ┌──────────────────────────────────────┐
        │     pipeline-controller (Spring)     │
        │  ┌────────────────────────────────┐  │
        │  │ MetricsPoller (every 10s)      │  │
        │  └─────────────┬──────────────────┘  │
        │                ▼                      │
        │  ┌────────────────────────────────┐  │
        │  │ Controller (decision engine)   │  │
        │  │  - 2-threshold per metric      │  │
        │  │  - cooldown per lever          │  │
        │  │  - lever priority ordering     │  │
        │  └─┬─────────────────┬────────────┘  │
        │    ▼                 ▼                │
        │  EmqxActuator     MqttActuator       │
        └────┼─────────────────┼────────────────┘
             │                 │
       HTTP API            publish
       (EMQX REST)         (cmd/control/<thing>)
```

One process, two actuators, one decision loop. No persistence — state is in-memory, baseline restored on restart.

---

## Levers

| Tier | Lever | Bounds | Actuator | Speed | Reversible? |
|---|---|---|---|---|---|
| 1 | EMQX `max_inflight` | 5 → 50 | `EmqxActuator` (HTTP PUT `/api/v5/actions/kafka_producer:dt_to_kafka`) | Fast | Yes |
| 1 | EMQX `max_batch_bytes` | 896KB → 2MB | EmqxActuator | Fast | Yes |
| 1 | EMQX `max_linger_time` | 50ms → 200ms | EmqxActuator | Fast | Yes |
| 2 | PIP threshold % (load shed) | -50% → +200% (relative to baseline) | `MqttActuator` (publish `cmd/control/<thing-id>`) | Slow propagation | Yes |

**Notes:**
- PIP threshold is cumulative (per `device.Manager.AdjustThreshold`); the controller tracks total adjustment per device to compute the "back to baseline" delta when relaxing.
- When EMQX-side levers are exhausted *and* consumer lag is rising, PIP shedding is the only remaining option in this scope. Operator must intervene (add partitions / replicas) for a permanent fix.

---

## Input Metrics (from Prometheus)

| Metric | Source | Used for |
|---|---|---|
| `kafka_consumergroup_lag{group="timescale-writer-group"}` | kafka-exporter | Detect kafka→sink bottleneck |
| `emqx_action_queuing{id="dt_to_kafka"}` | EMQX (must enable Prometheus) | Detect emqx→kafka bottleneck |
| `emqx_action_dropped{...}` | EMQX | Confirm overflow happened (urgent escalation) |
| `emqx_action_inflight{...}` | EMQX | Detect if max_inflight is the binding constraint |

EMQX Prometheus endpoint must be enabled in `infrastructure/emqx-dt.conf` and scrape target added to `infrastructure/prometheus.yml`.

---

## Decision Logic

**Two-threshold model per metric** (hysteresis — prevents thrashing):

```
HIGH threshold → ratchet up (apply next lever in priority order)
LOW threshold  → ratchet down (relax most-recently-bumped lever)
between        → hold steady
```

**Cooldown**: after any actuation, that lever is locked for `cooldownSeconds` (default 60s) before it can move again. Prevents oscillation when an action takes time to show in metrics.

**Decision tree** (evaluated each poll cycle, every 10s):

```
emqx_queuing > HIGH (e.g., 50k msgs)?
  ├── inflight at max_inflight cap & max_inflight < 50? → bump max_inflight
  ├── else max_batch_bytes < cap?                       → bump max_batch_bytes
  ├── else max_linger_time < cap?                       → bump max_linger_time
  └── else (Tier 1 exhausted)                           → PIP +20% (shed load)

consumer_lag > HIGH (e.g., 100k msgs)?
  └── PIP +20% (shed load — operator must scale to fix permanently)

emqx_dropped increasing?  (urgent — bypass cooldown)
  └── PIP +50% immediately, then queue Tier 1 actions

ALL metrics < LOW for sustained period (e.g., 5 min)?
  └── walk one lever back toward baseline (most-recent-bumped first)
```

**Lever priority** (within ratcheting up, prefer cheap/reversible first): EMQX `max_inflight` → `max_batch_bytes` → `max_linger_time` → PIP shedding.

---

## Project Structure

New top-level directory `pipeline-controller/` (sibling to `edge-device/`, `sink-service/`).

```
pipeline-controller/
├── pom.xml                          # Spring Boot 3.x, Java 21
├── Dockerfile
├── src/main/java/.../controller/
│   ├── PipelineControllerApplication.java
│   ├── poller/
│   │   ├── MetricsPoller.java       # @Scheduled, calls PrometheusClient
│   │   └── PrometheusClient.java    # WebClient, /api/v1/query
│   ├── decision/
│   │   ├── DecisionEngine.java      # decision tree, tracks lever state
│   │   ├── LeverState.java          # current value + lastChangedAt per lever
│   │   └── Thresholds.java          # HIGH/LOW per metric (from config)
│   ├── actuator/
│   │   ├── EmqxActuator.java        # WebClient PUT to EMQX REST API
│   │   └── MqttActuator.java        # Eclipse Paho publish
│   └── config/
│       └── ControllerProperties.java # @ConfigurationProperties
└── src/main/resources/
    └── application.yml              # endpoints, thresholds, bounds, cooldowns
```

**Dependencies** (in `pom.xml`):
- `spring-boot-starter-webflux` (WebClient for Prometheus + EMQX)
- `org.eclipse.paho.client.mqttv3` (MQTT publish)
- `spring-boot-starter-actuator` (health/metrics endpoint for the controller itself)

---

## Critical Files to Create / Modify

**New:**
- `pipeline-controller/` — entire new module (see structure above)

**Modify (infrastructure):**
- `infrastructure/emqx-dt.conf` — add `prometheus { enable = true, listeners.http.bind = 18084 }`. Same for `emqx-raw.conf` (different port).
- `infrastructure/prometheus.yml` — add scrape targets for both EMQX Prometheus endpoints.
- `infrastructure/docker-compose.yml`:
  - Expose new EMQX Prometheus ports
  - Add `pipeline-controller` service (build from `../pipeline-controller`)

**Reuse (no changes needed):**
- `edge-device/device/manager.go:218-243` — `AdjustThreshold` already accepts cumulative percentages via MQTT. Controller just publishes; existing handler does the work.
- `edge-device/device/manager.go:253-256` — `ControlMessage` JSON shape (`datastream_id?`, `percentage`).

---

## Configuration (application.yml)

```yaml
controller:
  poll-interval-seconds: 10
  cooldown-seconds: 60
  relax-after-healthy-seconds: 300

  prometheus:
    url: http://prometheus:9090

  emqx:
    api-url: http://emqx-dt:18083/api/v5
    action-id: kafka_producer:dt_to_kafka
    username: admin
    password: public

  mqtt:
    broker: tcp://emqx-dt:1883
    thing-ids: [device-001]   # devices to control

  thresholds:
    emqx-queuing:    { high: 50000, low: 5000 }
    consumer-lag:    { high: 100000, low: 10000 }
    emqx-dropped:    { urgent-delta: 100 }   # any increase triggers shed

  bounds:
    max-inflight:    { min: 5,    max: 50,   step: 5 }
    max-batch-bytes: { min: 917504, max: 2097152, step: 262144 }
    max-linger-time-ms: { min: 50, max: 200, step: 25 }
    pip-percentage:  { min: -50,  max: 200,  step: 20 }
```

---

## Verification

End-to-end test plan:

1. **Bring up the stack** with the controller and confirm baseline:
   ```bash
   docker compose -f infrastructure/docker-compose.yml up -d
   curl http://localhost:8081/actuator/health   # controller health
   ```
   - Controller logs should show "starting at baseline: max_inflight=5, max_batch_bytes=896KB, max_linger=50ms, pip=0%"

2. **Generate sustained load** by spinning up multiple edge devices (or one device with high publish rate). Watch:
   - EMQX dashboard → `dt_to_kafka` → Queuing climbs past 50k
   - Controller logs show: `bumping max_inflight 5 → 10`, then 10 → 15, etc.
   - EMQX dashboard reflects the new value live.

3. **Push past Tier 1 ceiling**: keep load high until all EMQX levers are at max. Confirm:
   - Controller falls through to PIP: publishes `{"percentage": 20}` on `cmd/control/device-001`
   - Edge device log: `pip.SetThreshold` updated
   - Raw vs dt MQTT topic message rate ratio drops (fewer dt messages).

4. **Trigger consumer-lag path**: stop `sink-service`, let lag accumulate past 100k. Restart and confirm:
   - Controller publishes PIP shed message (no replica scaling — by design).
   - Once lag drains and metric falls below LOW, after `relax-after-healthy-seconds` controller publishes `{"percentage": -20}` to walk PIP back.

5. **Verify lever state and relaxation**:
   - Hit controller's `/actuator/info` (custom endpoint exposing current lever state) — should show last action, current values, cooldown timers.
   - After sustained healthy period, verify controller walks levers back toward baseline one at a time (most-recent-bumped first).

---

## Out of Scope (v1)

- **Kafka partition count adjustment** — operator manages via `kafka-init` config.
- **Sink-service horizontal scaling** — operator manages via `docker compose --scale`.
- **TimescaleDB tuning** (chunk interval, compression, connection pool) — admin operations, not runtime tunable without restart.
- **Persistent state across restarts** — in-memory is fine for a thesis demo.
- **Per-datastream PIP tuning** — apply globally to all devices in `mqtt.thing-ids`.
- **Sink-service per-instance batch tuning** — would require invasive changes to `sink-service/main.go` for marginal gain.
- **Authentication on the controller's actuator endpoints**.
- **Multi-broker Kafka considerations** (replication factor stays 1).
