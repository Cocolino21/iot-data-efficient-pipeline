package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"sort"
	"strconv"
	"strings"
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

type logRouter struct {
	mu     sync.Mutex
	live   bool
	filter string
	buf    []string
}

const logBufLines = 500

func (r *logRouter) Write(p []byte) (int, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	line := string(p)
	r.buf = append(r.buf, line)
	if len(r.buf) > logBufLines {
		r.buf = r.buf[len(r.buf)-logBufLines:]
	}
	if r.live && (r.filter == "" || strings.Contains(line, r.filter)) {
		os.Stdout.Write(p)
	}
	return len(p), nil
}

func (r *logRouter) setLive(v bool, filter string) {
	r.mu.Lock()
	r.live = v
	r.filter = filter
	r.mu.Unlock()
}

func (r *logRouter) tail(n int, filter string) {
	r.mu.Lock()
	var lines []string
	for _, l := range r.buf {
		if filter == "" || strings.Contains(l, filter) {
			lines = append(lines, l)
		}
	}
	r.mu.Unlock()
	if len(lines) > n {
		lines = lines[len(lines)-n:]
	}
	for _, l := range lines {
		os.Stdout.WriteString(l)
	}
}

type deviceInstance struct {
	num       int
	thingID   string
	manager   *device.Manager
	client    *mqtt.Client
	rawClient *mqtt.Client
}

func startDevice(mc *MultiConfig, deviceNum int) (deviceInstance, error) {
	cfg := buildDeviceConfig(mc, deviceNum)
	thingID := cfg.Device.ThingID

	dtClientID := fmt.Sprintf("iot-edge-%s", thingID)
	dtClient, err := mqtt.NewClient(cfg.Device.MQTT.DTBrokerAddr(), dtClientID)
	if err != nil {
		return deviceInstance{}, fmt.Errorf("dt MQTT connect failed: %w", err)
	}

	var rawClient *mqtt.Client
	if mc.PublishRaw && mc.RawBroker != "" {
		rawClientID := fmt.Sprintf("iot-edge-raw-%s", thingID)
		rawClient, err = mqtt.NewClient(mc.RawBroker, rawClientID)
		if err != nil {
			dtClient.Close()
			return deviceInstance{}, fmt.Errorf("raw MQTT connect failed: %w", err)
		}
	}

	fail := func(err error) (deviceInstance, error) {
		dtClient.Close()
		if rawClient != nil {
			rawClient.Close()
		}
		return deviceInstance{}, err
	}

	mgr := device.NewManager(dtClient, rawClient, cfg, "")

	for _, sc := range cfg.Device.Sensors {
		if err := mgr.AddSensor(sc); err != nil {
			return fail(fmt.Errorf("adding sensor %s: %w", sc.DatastreamID, err))
		}
	}

	controlTopic := fmt.Sprintf("cmd/control/%s", thingID)
	if err := dtClient.Subscribe(controlTopic, 2, mgr.HandleControlMessage); err != nil {
		return fail(fmt.Errorf("subscribing to %s: %w", controlTopic, err))
	}

	if err := dtClient.Subscribe("cmd/control/broadcast", 2, mgr.HandleControlMessage); err != nil {
		return fail(fmt.Errorf("subscribing to broadcast control: %w", err))
	}

	configTopic := fmt.Sprintf("cmd/config/%s", thingID)
	if err := dtClient.Subscribe(configTopic, 2, mgr.HandleConfigMessage); err != nil {
		return fail(fmt.Errorf("subscribing to %s: %w", configTopic, err))
	}

	return deviceInstance{num: deviceNum, thingID: thingID, manager: mgr, client: dtClient, rawClient: rawClient}, nil
}

func stopDevice(d deviceInstance) {
	d.manager.Stop()
	d.client.Close()
	if d.rawClient != nil {
		d.rawClient.Close()
	}
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

func addDevices(mc *MultiConfig, n int, devices *[]deviceInstance, mu *sync.Mutex) {
	for i := 0; i < n; i++ {
		mu.Lock()
		next := len(*devices) + 1
		seedCfg := *mc
		if len(*devices) > 0 {
			st := (*devices)[0].manager.Status()
			if len(st.Sensors) > 0 {
				sum := 0.0
				for _, s := range st.Sensors {
					sum += s.Threshold
				}
				seedCfg.PIP.Threshold = sum / float64(len(st.Sensors))
			}
		}
		mu.Unlock()

		d, err := startDevice(&seedCfg, next)
		if err != nil {
			fmt.Printf("  ✗ %s-%03d failed: %v (stopping add)\n", mc.DevicePrefix, next, err)
			return
		}

		mu.Lock()
		*devices = append(*devices, d)
		mu.Unlock()

		fmt.Printf("  ✓ %s started (%d sensors, publish_raw=%v, threshold=%.2f)\n", d.thingID, len(mc.Sensors), mc.PublishRaw, seedCfg.PIP.Threshold)
	}
}

func removeDevices(n int, devices *[]deviceInstance, mu *sync.Mutex) {
	mu.Lock()
	if n > len(*devices) {
		n = len(*devices)
	}
	removed := (*devices)[len(*devices)-n:]
	*devices = (*devices)[:len(*devices)-n]
	mu.Unlock()

	for i := len(removed) - 1; i >= 0; i-- {
		stopDevice(removed[i])
		fmt.Printf("  ✗ %s stopped\n", removed[i].thingID)
	}
}

func runCommandLoop(mc *MultiConfig, router *logRouter, devices *[]deviceInstance, mu *sync.Mutex) {
	const usage = "commands: add <n> | remove <n> | count | log [adj|<text>]"
	scanner := bufio.NewScanner(os.Stdin)
	fmt.Print("> ")
	for scanner.Scan() {
		fields := strings.Fields(scanner.Text())
		if len(fields) == 0 {
			fmt.Print("> ")
			continue
		}
		cmd := strings.ToLower(fields[0])

		var n int
		if cmd == "add" || cmd == "remove" {
			if len(fields) != 2 {
				fmt.Println(usage)
				fmt.Print("> ")
				continue
			}
			var err error
			n, err = strconv.Atoi(fields[1])
			if err != nil || n < 1 {
				fmt.Printf("invalid count %q — %s\n", fields[1], usage)
				fmt.Print("> ")
				continue
			}
		}

		switch cmd {
		case "add":
			addDevices(mc, n, devices, mu)
		case "remove":
			removeDevices(n, devices, mu)
		case "count":
			mu.Lock()
			fmt.Printf("%d devices running\n", len(*devices))
			mu.Unlock()
		case "log":
			filter := ""
			if len(fields) > 1 {
				filter = strings.Join(fields[1:], " ")
				if filter == "adj" {
					filter = "threshold"
				}
			}
			if filter == "" {
				fmt.Println("-- live log (press Enter or q+Enter to return) --")
			} else {
				fmt.Printf("-- live log filtered by %q (press Enter or q+Enter to return) --\n", filter)
			}
			router.tail(15, filter)
			router.setLive(true, filter)
			scanner.Scan()
			router.setLive(false, "")
			fmt.Println("-- log paused --")
		default:
			fmt.Println(usage)
		}
		fmt.Print("> ")
	}
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

	router := &logRouter{}
	log.SetOutput(router)

	fmt.Printf("Starting %d simulated devices (prefix=%s)\n", mc.DeviceCount, mc.DevicePrefix)

	devices := make([]deviceInstance, 0, mc.DeviceCount)
	var mu sync.Mutex

	startStatusServer(mc.StatusPort, &devices, &mu)

	for i := 1; i <= mc.DeviceCount; i++ {
		d, err := startDevice(mc, i)
		if err != nil {
			log.Fatalf("device %s-%03d: %v", mc.DevicePrefix, i, err)
		}

		mu.Lock()
		devices = append(devices, d)
		mu.Unlock()

		fmt.Printf("  ✓ %s started (%d sensors, publish_raw=%v)\n", d.thingID, len(mc.Sensors), mc.PublishRaw)
	}

	fmt.Printf("All %d devices running. Commands: add <n> | remove <n> | count | log. Ctrl+C to stop.\n", mc.DeviceCount)

	go runCommandLoop(mc, router, &devices, &mu)

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt)
	<-stop

	fmt.Println("\nShutting down all devices...")
	mu.Lock()
	remaining := devices
	devices = nil
	mu.Unlock()
	for _, d := range remaining {
		stopDevice(d)
	}
	fmt.Println("All devices stopped.")
}
