package sensor

import (
	"math"
	"math/rand"
	"time"
)

type Temperature struct {
	interval time.Duration
	hvac     float64 // current HVAC perturbation, drifts over time
}

func NewTemperature(interval time.Duration) *Temperature {
	return &Temperature{
		interval: interval,
		hvac:     0,
	}
}

func (t *Temperature) ReadValue() Reading {
	time.Sleep(t.interval)

	now := time.Now()
	minuteOfDay := float64(now.Hour()*60 + now.Minute())

	base := 22 + 3*math.Sin(2*math.Pi*(minuteOfDay-300)/1440)

	if rand.Float64() < 0.05 {
		if rand.Float64() < 0.5 {
			t.hvac += 0.3
		} else {
			t.hvac -= 0.3
		}
	} else {
		t.hvac *= 0.995
	}
	t.hvac = math.Max(-2, math.Min(2, t.hvac))

	return Reading{
		Timestamp: now,
		Value:     base + t.hvac,
	}
}
