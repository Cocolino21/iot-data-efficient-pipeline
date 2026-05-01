package sensor

import (
	"math"
	"math/rand"
	"time"
)

type Humidity struct {
	interval     time.Duration
	value        float64
	lastTempBase float64
}

func NewHumidity(interval time.Duration) *Humidity {
	now := time.Now()
	minuteOfDay := float64(now.Hour()*60 + now.Minute())
	tempBase := 22 + 3*math.Sin(2*math.Pi*(minuteOfDay-300)/1440)

	return &Humidity{
		interval:     interval,
		value:        50.0,
		lastTempBase: tempBase,
	}
}

func (h *Humidity) ReadValue() Reading {
	time.Sleep(h.interval)

	now := time.Now()
	minuteOfDay := float64(now.Hour()*60 + now.Minute())

	// compute temperature base to derive the delta
	tempBase := 22 + 3*math.Sin(2*math.Pi*(minuteOfDay-300)/1440)
	tempDelta := tempBase - h.lastTempBase
	h.lastTempBase = tempBase

	// anti-correlated drift from temperature + small random noise
	drift := -0.3*tempDelta + rand.NormFloat64()*0.1
	h.value += drift
	h.value = math.Max(30, math.Min(70, h.value))

	return Reading{
		Timestamp: now,
		Value:     h.value,
	}
}
