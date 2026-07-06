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
	sensor         sensor.Sensor
	filter         *pip.Filter
	stop           chan struct{}
	datastreamID   string
	sensorType     string
	interval       time.Duration
	datasetDir     string
	datasetFile    string
	enabled        bool
	rawActive      bool    // currently in raw/calibration mode
	rawGen         int     // generation guard for the TTL timer
	savedThreshold float64 // threshold to restore when raw mode ends
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
					if err := m.rawClient.Publish("raw", m.thingID, entry.datastreamID, entry.sensorType, reading.Timestamp, reading.Value); err != nil {
						log.Printf("[%s] raw publish error: %v", entry.datastreamID, err)
					}
				}

				forward := entry.filter.Process(reading.Timestamp, reading.Value)
				if forward {
					if err := m.client.Publish("dt", m.thingID, entry.datastreamID, entry.sensorType, reading.Timestamp, reading.Value); err != nil {
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
			DatasetDir:   entry.datasetDir,
			DatasetFile:  entry.datasetFile,
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

func (m *Manager) AddSensor(sc config.SensorConfig) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if _, exists := m.sensors[sc.DatastreamID]; exists {
		return fmt.Errorf("sensor %s already exists", sc.DatastreamID)
	}

	interval, err := time.ParseDuration(sc.Interval)
	if err != nil {
		return fmt.Errorf("invalid interval %q: %w", sc.Interval, err)
	}

	s, err := sensor.New(sc.Type, interval, sc.DatasetDir, sc.DatasetFile)
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
		datastreamID: sc.DatastreamID,
		sensorType:   sc.Type,
		interval:     interval,
		datasetDir:   sc.DatasetDir,
		datasetFile:  sc.DatasetFile,
		enabled:      true,
	}

	m.sensors[sc.DatastreamID] = entry
	m.startSensor(entry)
	log.Printf("[%s] sensor added (type=%s, interval=%s)", sc.DatastreamID, sc.Type, sc.Interval)

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

// threshold bounds: PIP threshold is kept within [minThreshold, maxThreshold].
// minThreshold 0 = forward everything (no reduction); maxThreshold 1 = forward nothing.
// minRiseThreshold is the lift-off base used when an increase is applied to a
// threshold at/near 0 — the update is multiplicative, so 0 * anything stays 0.
const (
	minThreshold     = 0.00
	maxThreshold     = 1.00
	minRiseThreshold = 0.01
)

func clampThreshold(v float64) float64 {
	if v < minThreshold {
		return minThreshold
	}
	if v > maxThreshold {
		return maxThreshold
	}
	return v
}

// adjustThreshold applies a percentage change to the current threshold. When
// increasing from at/near zero, it lifts the base to minRiseThreshold first so
// the multiplicative update can take effect (0 * anything would stay 0).
func adjustThreshold(current, percentage float64) float64 {
	base := current
	if percentage > 0 && base < minRiseThreshold {
		base = minRiseThreshold
	}
	return clampThreshold(base * (1 + percentage/100))
}

func (m *Manager) AdjustThreshold(datastreamID string, percentage float64) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if datastreamID == "" {
		for id, entry := range m.sensors {
			if entry.rawActive {
				// Calibrating: PIP is bypassed so the live threshold is
				// irrelevant, but keep savedThreshold tracking the broadcasts
				// peers receive so we restore to a peer-consistent value when
				// raw mode ends.
				old := entry.savedThreshold
				entry.savedThreshold = adjustThreshold(old, percentage)
				log.Printf("[%s] RAW: folded %+.0f%% into saved threshold %.4f -> %.4f", id, percentage, old, entry.savedThreshold)
				continue
			}
			current := entry.filter.Threshold()
			newVal := adjustThreshold(current, percentage)
			entry.filter.SetThreshold(newVal)
			log.Printf("[%s] threshold adjusted %.2f -> %.2f (%+.0f%%)", id, current, newVal, percentage)
		}
		return nil
	}

	entry, exists := m.sensors[datastreamID]
	if !exists {
		return fmt.Errorf("sensor %s not found", datastreamID)
	}

	if entry.rawActive {
		log.Printf("[%s] in RAW mode, ignoring threshold adjustment", datastreamID)
		return nil
	}

	current := entry.filter.Threshold()
	newVal := adjustThreshold(current, percentage)
	entry.filter.SetThreshold(newVal)
	log.Printf("[%s] threshold adjusted %.2f -> %.2f (%+.0f%%)", datastreamID, current, newVal, percentage)

	return nil
}

type ConfigMessage struct {
	Action       string                `json:"action"`
	DatastreamID string                `json:"datastream_id,omitempty"`
	Type         string                `json:"type,omitempty"`
	Interval     string                `json:"interval,omitempty"`
	DatasetDir   string                `json:"dataset_dir,omitempty"`
	DatasetFile  string                `json:"dataset_file,omitempty"`
	Sensors      []config.SensorConfig `json:"sensors,omitempty"`
}

type ControlMessage struct {
	DatastreamID string  `json:"datastream_id,omitempty"`
	Percentage   float64 `json:"percentage"`
	Mode         string  `json:"mode,omitempty"` // "raw" = stop thresholding for ttl_s seconds
	TTLSeconds   int     `json:"ttl_s,omitempty"`
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
		err = m.AddSensor(config.SensorConfig{
			DatastreamID: msg.DatastreamID,
			Type:         msg.Type,
			Interval:     msg.Interval,
			DatasetDir:   msg.DatasetDir,
			DatasetFile:  msg.DatasetFile,
		})
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

		s, err := sensor.New(sc.Type, interval, sc.DatasetDir, sc.DatasetFile)
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
			datasetDir:   sc.DatasetDir,
			datasetFile:  sc.DatasetFile,
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

	if msg.Mode == "raw" {
		m.EnterRawMode(msg.DatastreamID, msg.TTLSeconds)
		return
	}

	if err := m.AdjustThreshold(msg.DatastreamID, msg.Percentage); err != nil {
		log.Printf("control error: %v", err)
	}
}

// EnterRawMode disables PIP thresholding for the given datastream (or all
// datastreams when datastreamID is empty) so it publishes every reading, then
// auto-reverts after ttlSeconds and restores the previous threshold. The TTL is
// self-enforced on the device, so a missed/lost "revert" command can never
// strand a stream in raw mode. While raw, shed/relax commands are ignored.
//
// Safe on the broadcast topic: if this manager does not own the datastream
// (e.g. a broadcast targeting another device in the multi-simulator), it is a
// silent no-op.
func (m *Manager) EnterRawMode(datastreamID string, ttlSeconds int) {
	if ttlSeconds <= 0 {
		log.Printf("raw mode: invalid ttl_s %d, ignoring", ttlSeconds)
		return
	}
	ttl := time.Duration(ttlSeconds) * time.Second

	m.mu.Lock()
	defer m.mu.Unlock()

	arm := func(id string, entry *sensorEntry) {
		if !entry.rawActive {
			entry.savedThreshold = entry.filter.Threshold()
			entry.rawActive = true
			entry.filter.SetBypass(true)
		}
		entry.rawGen++
		gen := entry.rawGen
		time.AfterFunc(ttl, func() { m.exitRawMode(id, gen) })
		log.Printf("[%s] RAW mode for %s (saved threshold %.4f)", id, ttl, entry.savedThreshold)
	}

	if datastreamID == "" {
		for id, entry := range m.sensors {
			arm(id, entry)
		}
		return
	}
	if entry, ok := m.sensors[datastreamID]; ok {
		arm(datastreamID, entry)
	}
	// otherwise: not this device's datastream — ignore silently.
}

// exitRawMode is fired by a stream's TTL timer. It only reverts if this is still
// the most recent raw window for the stream (rawGen guard), so a stale timer
// from a superseded window can't cut a newer one short.
func (m *Manager) exitRawMode(datastreamID string, gen int) {
	m.mu.Lock()
	defer m.mu.Unlock()

	entry, ok := m.sensors[datastreamID]
	if !ok || !entry.rawActive || entry.rawGen != gen {
		return
	}
	entry.filter.SetBypass(false)
	entry.filter.SetThreshold(entry.savedThreshold)
	entry.rawActive = false
	log.Printf("[%s] RAW mode ended, threshold restored to %.4f", datastreamID, entry.savedThreshold)
}

func (m *Manager) WaitForInit(timeout time.Duration) bool {
	configTopic := fmt.Sprintf("cmd/config/%s", m.thingID)
	initCh := make(chan []byte, 1)

	if err := m.client.Subscribe(configTopic, 2, func(_ string, payload []byte) {
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
