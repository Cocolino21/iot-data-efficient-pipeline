package device

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"sync"
	"time"

	"iot-edge-device/config"
	"iot-edge-device/mqtt"
	"iot-edge-device/pip"
	"iot-edge-device/sensor"
)

type sensorEntry struct {
	sensor       sensor.Sensor
	filter       *pip.Filter
	stop         chan struct{}
	datastreamID string
	sensorType   string
	interval     time.Duration
	enabled      bool
}

type Manager struct {
	mu         sync.Mutex
	sensors    map[string]*sensorEntry
	client     *mqtt.Client
	rawClient  *mqtt.Client
	thingID    string
	pipCfg     config.PIPConfig
	simulated  bool
	configPath string
	cfg        *config.Config
	wg         sync.WaitGroup
}

func (m *Manager) startSensor(entry *sensorEntry) {
	stop := entry.stop
	m.wg.Add(1)
	go func() {
		defer m.wg.Done()
		for {
			select {
			case <-stop:
				return
			default:
				reading := entry.sensor.ReadValue()

				if m.rawClient != nil {
					if err := m.rawClient.Publish("raw", m.thingID, entry.datastreamID, reading.Timestamp, reading.Value); err != nil {
						log.Printf("[%s] raw publish error: %v", entry.datastreamID, err)
					}
				}

				forward := entry.filter.Process(reading.Timestamp, reading.Value)
				if forward {
					if err := m.client.Publish("dt", m.thingID, entry.datastreamID, reading.Timestamp, reading.Value); err != nil {
						log.Printf("[%s] dt publish error: %v", entry.datastreamID, err)
					}
				}

				fmt.Printf("[%s] value=%.2f forwarded=%v\n", entry.datastreamID, reading.Value, forward)
			}
		}
	}()
}

func (m *Manager) persistConfig() error {
	if m.configPath == "" {
		return nil
	}
	sensors := make([]config.SensorConfig, 0, len(m.sensors))
	for _, entry := range m.sensors {
		enabled := entry.enabled
		sensors = append(sensors, config.SensorConfig{
			DatastreamID: entry.datastreamID,
			Type:         entry.sensorType,
			Interval:     entry.interval.String(),
			Enabled:      &enabled,
		})
	}

	m.cfg.Device.Sensors = sensors

	data, err := json.MarshalIndent(m.cfg, "", "  ")
	if err != nil {
		return fmt.Errorf("marshaling config: %w", err)
	}

	if err := os.WriteFile(m.configPath, data, 0644); err != nil {
		return fmt.Errorf("writing config: %w", err)
	}

	return nil
}

func (m *Manager) AddSensor(datastreamID, sensorType, intervalStr string) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if _, exists := m.sensors[datastreamID]; exists {
		return fmt.Errorf("sensor %s already exists", datastreamID)
	}

	interval, err := time.ParseDuration(intervalStr)
	if err != nil {
		return fmt.Errorf("invalid interval %q: %w", intervalStr, err)
	}

	s, err := sensor.New(sensorType, interval)
	if err != nil {
		return err
	}

	maxInterval, err := time.ParseDuration(m.pipCfg.MaxInterval)
	if err != nil {
		return fmt.Errorf("invalid pip max_interval: %w", err)
	}

	filter := pip.NewFilter(m.pipCfg.CacheSize, m.pipCfg.Threshold, maxInterval)

	entry := &sensorEntry{
		sensor:       s,
		filter:       filter,
		stop:         make(chan struct{}),
		datastreamID: datastreamID,
		sensorType:   sensorType,
		interval:     interval,
		enabled:      true,
	}

	m.sensors[datastreamID] = entry
	m.startSensor(entry)
	log.Printf("[%s] sensor added (type=%s, interval=%s)", datastreamID, sensorType, intervalStr)

	if err := m.persistConfig(); err != nil {
		log.Printf("persist error: %v", err)
	}

	return nil
}

func (m *Manager) RemoveSensor(datastreamID string) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	entry, exists := m.sensors[datastreamID]
	if !exists {
		return fmt.Errorf("sensor %s not found", datastreamID)
	}

	if entry.enabled {
		close(entry.stop)
	}

	delete(m.sensors, datastreamID)
	log.Printf("[%s] sensor removed", datastreamID)

	if err := m.persistConfig(); err != nil {
		log.Printf("persist error: %v", err)
	}

	return nil
}

func (m *Manager) EnableSensor(datastreamID string) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	entry, exists := m.sensors[datastreamID]
	if !exists {
		return fmt.Errorf("sensor %s not found", datastreamID)
	}

	if entry.enabled {
		return nil
	}

	entry.stop = make(chan struct{})
	entry.enabled = true
	m.startSensor(entry)
	log.Printf("[%s] sensor enabled", datastreamID)

	if err := m.persistConfig(); err != nil {
		log.Printf("persist error: %v", err)
	}

	return nil
}

func (m *Manager) DisableSensor(datastreamID string) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	entry, exists := m.sensors[datastreamID]
	if !exists {
		return fmt.Errorf("sensor %s not found", datastreamID)
	}

	if !entry.enabled {
		return nil
	}

	close(entry.stop)
	entry.enabled = false
	log.Printf("[%s] sensor disabled", datastreamID)

	if err := m.persistConfig(); err != nil {
		log.Printf("persist error: %v", err)
	}

	return nil
}

func (m *Manager) AdjustThreshold(datastreamID string, percentage float64) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if datastreamID == "" {
		for id, entry := range m.sensors {
			current := entry.filter.Threshold()
			newVal := current * (1 + percentage/100)
			entry.filter.SetThreshold(newVal)
			log.Printf("[%s] threshold adjusted %.2f -> %.2f (%+.0f%%)", id, current, newVal, percentage)
		}
		return nil
	}

	entry, exists := m.sensors[datastreamID]
	if !exists {
		return fmt.Errorf("sensor %s not found", datastreamID)
	}

	current := entry.filter.Threshold()
	newVal := current * (1 + percentage/100)
	entry.filter.SetThreshold(newVal)
	log.Printf("[%s] threshold adjusted %.2f -> %.2f (%+.0f%%)", datastreamID, current, newVal, percentage)

	return nil
}

type ConfigMessage struct {
	Action       string                `json:"action"`
	DatastreamID string                `json:"datastream_id,omitempty"`
	Type         string                `json:"type,omitempty"`
	Interval     string                `json:"interval,omitempty"`
	Sensors      []config.SensorConfig `json:"sensors,omitempty"`
}

type ControlMessage struct {
	DatastreamID string  `json:"datastream_id,omitempty"`
	Percentage   float64 `json:"percentage"`
}

func (m *Manager) HandleConfigMessage(_ string, payload []byte) {
	var msg ConfigMessage
	if err := json.Unmarshal(payload, &msg); err != nil {
		log.Printf("invalid config message: %v", err)
		return
	}

	var err error
	switch msg.Action {
	case "init":
		err = m.initSensors(msg.Sensors)
	case "add":
		err = m.AddSensor(msg.DatastreamID, msg.Type, msg.Interval)
	case "remove":
		err = m.RemoveSensor(msg.DatastreamID)
	case "enable":
		err = m.EnableSensor(msg.DatastreamID)
	case "disable":
		err = m.DisableSensor(msg.DatastreamID)
	default:
		log.Printf("unknown config action: %s", msg.Action)
		return
	}

	if err != nil {
		log.Printf("config %s error: %v", msg.Action, err)
	}
}

func (m *Manager) initSensors(sensors []config.SensorConfig) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	for _, entry := range m.sensors {
		if entry.enabled {
			close(entry.stop)
		}
	}
	m.sensors = make(map[string]*sensorEntry)

	for _, sc := range sensors {
		interval, err := time.ParseDuration(sc.Interval)
		if err != nil {
			log.Printf("[%s] invalid interval %q, skipping: %v", sc.DatastreamID, sc.Interval, err)
			continue
		}

		s, err := sensor.New(sc.Type, interval)
		if err != nil {
			log.Printf("[%s] unknown type %q, skipping: %v", sc.DatastreamID, sc.Type, err)
			continue
		}

		maxInterval, err := time.ParseDuration(m.pipCfg.MaxInterval)
		if err != nil {
			log.Printf("[%s] invalid pip max_interval, skipping: %v", sc.DatastreamID, err)
			continue
		}

		filter := pip.NewFilter(m.pipCfg.CacheSize, m.pipCfg.Threshold, maxInterval)

		enabled := sc.Enabled == nil || *sc.Enabled
		entry := &sensorEntry{
			sensor:       s,
			filter:       filter,
			stop:         make(chan struct{}),
			datastreamID: sc.DatastreamID,
			sensorType:   sc.Type,
			interval:     interval,
			enabled:      enabled,
		}

		m.sensors[sc.DatastreamID] = entry
		if enabled {
			m.startSensor(entry)
		}
	}

	log.Printf("init: %d sensors configured", len(m.sensors))

	if err := m.persistConfig(); err != nil {
		log.Printf("persist error: %v", err)
	}

	return nil
}

func (m *Manager) HandleControlMessage(_ string, payload []byte) {
	var msg ControlMessage
	if err := json.Unmarshal(payload, &msg); err != nil {
		log.Printf("invalid control message: %v", err)
		return
	}

	if err := m.AdjustThreshold(msg.DatastreamID, msg.Percentage); err != nil {
		log.Printf("control error: %v", err)
	}
}

func (m *Manager) WaitForInit(timeout time.Duration) bool {
	configTopic := fmt.Sprintf("cmd/config/%s", m.thingID)
	initCh := make(chan []byte, 1)

	if err := m.client.Subscribe(configTopic, func(_ string, payload []byte) {
		var msg ConfigMessage
		if err := json.Unmarshal(payload, &msg); err != nil {
			return
		}
		if msg.Action == "init" {
			select {
			case initCh <- payload:
			default:
			}
		}
	}); err != nil {
		log.Printf("subscribe to %s failed: %v", configTopic, err)
		return false
	}

	log.Printf("waiting %s for init config on %s...", timeout, configTopic)

	select {
	case payload := <-initCh:
		var msg ConfigMessage
		json.Unmarshal(payload, &msg)
		m.initSensors(msg.Sensors)
		log.Printf("received init config from cloud")
		return true
	case <-time.After(timeout):
		log.Printf("no init config received, using local config.json")
		return false
	}
}

func (m *Manager) Stop() {
	m.mu.Lock()
	for _, entry := range m.sensors {
		if entry.enabled {
			close(entry.stop)
		}
	}
	m.mu.Unlock()
	m.wg.Wait()
}

// SensorStatus is a snapshot of a single sensor's runtime state.
type SensorStatus struct {
	DatastreamID string  `json:"datastream_id"`
	Type         string  `json:"type"`
	Interval     string  `json:"interval"`
	Enabled      bool    `json:"enabled"`
	Threshold    float64 `json:"threshold"`
}

type DeviceStatus struct {
	ThingID string         `json:"thing_id"`
	Sensors []SensorStatus `json:"sensors"`
}

func (m *Manager) Status() DeviceStatus {
	m.mu.Lock()
	defer m.mu.Unlock()

	sensors := make([]SensorStatus, 0, len(m.sensors))
	for _, entry := range m.sensors {
		sensors = append(sensors, SensorStatus{
			DatastreamID: entry.datastreamID,
			Type:         entry.sensorType,
			Interval:     entry.interval.String(),
			Enabled:      entry.enabled,
			Threshold:    entry.filter.Threshold(),
		})
	}

	return DeviceStatus{ThingID: m.thingID, Sensors: sensors}
}

func NewManager(client *mqtt.Client, rawClient *mqtt.Client, cfg *config.Config, configPath string) *Manager {
	return &Manager{
		sensors:    make(map[string]*sensorEntry),
		client:     client,
		rawClient:  rawClient,
		thingID:    cfg.Device.ThingID,
		pipCfg:     cfg.Control.PIP,
		simulated:  cfg.Device.Simulated,
		configPath: configPath,
		cfg:        cfg,
	}
}
