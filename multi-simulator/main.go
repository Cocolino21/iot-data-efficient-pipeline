package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"sort"
	"sync"

	"iot-edge-device/config"
	"iot-edge-device/device"
	"iot-edge-device/mqtt"
)

type MultiConfig struct {
	DeviceCount  int              `json:"device_count"`
	DevicePrefix string           `json:"device_prefix"`
	MQTTBroker   string           `json:"mqtt_broker"`
	RawBroker    string           `json:"raw_broker"`
	PublishRaw   bool             `json:"publish_raw"`
	StatusPort   int              `json:"status_port"`
	Sensors      []SensorTemplate `json:"sensors"`
	PIP          config.PIPConfig `json:"pip"`
}

type SensorTemplate struct {
	DatastreamID string `json:"datastream_id"`
	Type         string `json:"type"`
	Interval     string `json:"interval"`
	DatasetDir   string `json:"dataset_dir,omitempty"`
	DatasetFile  string `json:"dataset_file,omitempty"`
}

func loadMultiConfig(path string) (*MultiConfig, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var cfg MultiConfig
	if err := json.Unmarshal(data, &cfg); err != nil {
		return nil, err
	}
	return &cfg, nil
}

func buildDeviceConfig(mc *MultiConfig, deviceNum int) *config.Config {
	thingID := fmt.Sprintf("%s-%03d", mc.DevicePrefix, deviceNum)

	sensors := make([]config.SensorConfig, len(mc.Sensors))
	for i, s := range mc.Sensors {
		enabled := true
		sensors[i] = config.SensorConfig{
			// e.g. "temperature-7" for device-007's temperature sensor
			DatastreamID: fmt.Sprintf("%s-%d", s.DatastreamID, deviceNum),
			Type:         s.Type,
			Interval:     s.Interval,
			Enabled:      &enabled,
			DatasetDir:   s.DatasetDir,
			DatasetFile:  s.DatasetFile,
		}
	}

	return &config.Config{
		Device: config.DeviceConfig{
			ThingID: thingID,
			MQTT: config.MQTTConfig{
				DTBroker:   mc.MQTTBroker,
				RawBroker:  mc.RawBroker,
				PublishRaw: mc.PublishRaw,
			},
			Simulated: true,
			Sensors:   sensors,
		},
		Control: config.ControlConfig{
			PIP: mc.PIP,
		},
	}
}

type deviceInstance struct {
	manager   *device.Manager
	client    *mqtt.Client
	rawClient *mqtt.Client
}

type statusResponse struct {
	DeviceCount int                   `json:"device_count"`
	Devices     []device.DeviceStatus `json:"devices"`
}

func startStatusServer(port int, devices *[]deviceInstance, mu *sync.Mutex) {
	if port == 0 {
		port = 8090
	}
	http.HandleFunc("/status", func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		statuses := make([]device.DeviceStatus, len(*devices))
		for i, d := range *devices {
			statuses[i] = d.manager.Status()
		}
		mu.Unlock()

		// Sort by thing ID for stable output
		sort.Slice(statuses, func(i, j int) bool {
			return statuses[i].ThingID < statuses[j].ThingID
		})

		resp := statusResponse{DeviceCount: len(statuses), Devices: statuses}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	})
	log.Printf("Status API listening on http://localhost:%d/status", port)
	go http.ListenAndServe(fmt.Sprintf(":%d", port), nil)
}

func main() {
	cfgPath := "multi-config.json"
	if len(os.Args) > 1 {
		cfgPath = os.Args[1]
	}

	mc, err := loadMultiConfig(cfgPath)
	if err != nil {
		log.Fatalf("loading multi-config: %v", err)
	}

	log.Printf("Starting %d simulated devices (prefix=%s)", mc.DeviceCount, mc.DevicePrefix)

	devices := make([]deviceInstance, 0, mc.DeviceCount)
	var mu sync.Mutex

	startStatusServer(mc.StatusPort, &devices, &mu)

	for i := 1; i <= mc.DeviceCount; i++ {
		cfg := buildDeviceConfig(mc, i)

		dtClientID := fmt.Sprintf("iot-edge-%s", cfg.Device.ThingID)
		dtClient, err := mqtt.NewClient(cfg.Device.MQTT.DTBrokerAddr(), dtClientID)
		if err != nil {
			log.Fatalf("device %s: dt MQTT connect failed: %v", cfg.Device.ThingID, err)
		}

		var rawClient *mqtt.Client
		if mc.PublishRaw && mc.RawBroker != "" {
			rawClientID := fmt.Sprintf("iot-edge-raw-%s", cfg.Device.ThingID)
			rawClient, err = mqtt.NewClient(mc.RawBroker, rawClientID)
			if err != nil {
				log.Fatalf("device %s: raw MQTT connect failed: %v", cfg.Device.ThingID, err)
			}
		}

		mgr := device.NewManager(dtClient, rawClient, cfg, "")

		for _, sc := range cfg.Device.Sensors {
			if err := mgr.AddSensor(sc); err != nil {
				log.Fatalf("device %s: adding sensor %s: %v", cfg.Device.ThingID, sc.DatastreamID, err)
			}
		}

		controlTopic := fmt.Sprintf("cmd/control/%s", cfg.Device.ThingID)
		if err := dtClient.Subscribe(controlTopic, 2, mgr.HandleControlMessage); err != nil {
			log.Fatalf("device %s: subscribing to %s: %v", cfg.Device.ThingID, controlTopic, err)
		}

		if err := dtClient.Subscribe("cmd/control/broadcast", 2, mgr.HandleControlMessage); err != nil {
			log.Fatalf("device %s: subscribing to broadcast control: %v", cfg.Device.ThingID, err)
		}

		configTopic := fmt.Sprintf("cmd/config/%s", cfg.Device.ThingID)
		if err := dtClient.Subscribe(configTopic, 2, mgr.HandleConfigMessage); err != nil {
			log.Fatalf("device %s: subscribing to %s: %v", cfg.Device.ThingID, configTopic, err)
		}

		mu.Lock()
		devices = append(devices, deviceInstance{manager: mgr, client: dtClient, rawClient: rawClient})
		mu.Unlock()

		log.Printf("  ✓ %s started (%d sensors, publish_raw=%v)", cfg.Device.ThingID, len(cfg.Device.Sensors), mc.PublishRaw)
	}

	log.Printf("All %d devices running. Press Ctrl+C to stop.", mc.DeviceCount)

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt)
	<-stop

	fmt.Println("\nShutting down all devices...")
	for _, d := range devices {
		d.manager.Stop()
		d.client.Close()
		if d.rawClient != nil {
			d.rawClient.Close()
		}
	}
	log.Println("All devices stopped.")
}
