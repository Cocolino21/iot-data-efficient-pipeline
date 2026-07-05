package config

import (
	"encoding/json"
	"fmt"
	"os"
)

type SensorConfig struct {
	DatastreamID string `json:"datastream_id"`
	Type         string `json:"type"`
	Interval     string `json:"interval"`
	Enabled      *bool  `json:"enabled,omitempty"`
	DatasetDir   string `json:"dataset_dir,omitempty"`  // for type "ukdale": the house folder
	DatasetFile  string `json:"dataset_file,omitempty"` // for type "ukdale": defaults to mains.dat
}

type MQTTConfig struct {
	Broker     string `json:"broker,omitempty"` // legacy single-broker field
	DTBroker   string `json:"dt_broker,omitempty"`
	RawBroker  string `json:"raw_broker,omitempty"`
	PublishRaw bool   `json:"publish_raw"`
}

func (m MQTTConfig) DTBrokerAddr() string {
	if m.DTBroker != "" {
		return m.DTBroker
	}
	return m.Broker
}

type DeviceConfig struct {
	ThingID   string         `json:"thing_id"`
	MQTT      MQTTConfig     `json:"mqtt"`
	Simulated bool           `json:"simulated"`
	Sensors   []SensorConfig `json:"sensors"`
}

type PIPConfig struct {
	CacheSize   int     `json:"cache_size"`
	Threshold   float64 `json:"threshold"`
	MaxInterval string  `json:"max_interval"`
}

type ControlConfig struct {
	PIP PIPConfig `json:"pip"`
}

type Config struct {
	Device  DeviceConfig  `json:"device"`
	Control ControlConfig `json:"control"`
}

func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("reading config: %w", err)
	}

	var cfg Config
	if err := json.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("parsing config: %w", err)
	}

	for i := range cfg.Device.Sensors {
		if cfg.Device.Sensors[i].Enabled == nil {
			enabled := true
			cfg.Device.Sensors[i].Enabled = &enabled
		}
	}

	return &cfg, nil
}
