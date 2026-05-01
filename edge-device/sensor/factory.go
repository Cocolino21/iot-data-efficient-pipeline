package sensor

import (
	"fmt"
	"time"
)

func New(sensorType string, interval time.Duration) (Sensor, error) {
	switch sensorType {
	case "temperature":
		return NewTemperature(interval), nil
	case "humidity":
		return NewHumidity(interval), nil
	case "power":
		return NewPower(interval), nil
	case "light":
		return NewLight(interval), nil
	default:
		return nil, fmt.Errorf("unknown sensor type: %s", sensorType)
	}
}
