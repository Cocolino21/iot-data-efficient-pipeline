package main

import (
	"fmt"
	"log"
	"os"
	"os/signal"
	"time"

	"iot-edge-device/config"
	"iot-edge-device/device"
	"iot-edge-device/mqtt"
)

func main() {
	cfgPath := "config.json"
	if len(os.Args) > 1 {
		cfgPath = os.Args[1]
	}

	cfg, err := config.Load(cfgPath)
	if err != nil {
		log.Fatalf("loading config: %v", err)
	}

	dtClient, err := mqtt.NewClient(cfg.Device.MQTT.DTBrokerAddr(), fmt.Sprintf("iot-edge-%s", cfg.Device.ThingID))
	if err != nil {
		log.Fatalf("connecting to dt broker: %v", err)
	}
	defer dtClient.Close()

	var rawClient *mqtt.Client
	if cfg.Device.MQTT.PublishRaw && cfg.Device.MQTT.RawBroker != "" {
		rawClient, err = mqtt.NewClient(cfg.Device.MQTT.RawBroker, fmt.Sprintf("iot-edge-raw-%s", cfg.Device.ThingID))
		if err != nil {
			log.Fatalf("connecting to raw broker: %v", err)
		}
		defer rawClient.Close()
	}

	mgr := device.NewManager(dtClient, rawClient, cfg, cfgPath)

	cloudInit := mgr.WaitForInit(10 * time.Second)

	if !cloudInit {
		for _, sc := range cfg.Device.Sensors {
			if sc.Enabled != nil && !*sc.Enabled {
				continue
			}
			if err := mgr.AddSensor(sc); err != nil {
				log.Fatalf("adding sensor %s: %v", sc.DatastreamID, err)
			}
		}
	}

	configTopic := fmt.Sprintf("cmd/config/%s", cfg.Device.ThingID)
	if err := dtClient.Subscribe(configTopic, 2, mgr.HandleConfigMessage); err != nil {
		log.Fatalf("subscribing to %s: %v", configTopic, err)
	}

	controlTopic := fmt.Sprintf("cmd/control/%s", cfg.Device.ThingID)
	if err := dtClient.Subscribe(controlTopic, 2, mgr.HandleControlMessage); err != nil {
		log.Fatalf("subscribing to %s: %v", controlTopic, err)
	}

	log.Printf("device %s running (simulated=%v, publish_raw=%v)", cfg.Device.ThingID, cfg.Device.Simulated, cfg.Device.MQTT.PublishRaw)

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt)
	<-stop

	fmt.Println("\nShutting down...")
	mgr.Stop()
}
