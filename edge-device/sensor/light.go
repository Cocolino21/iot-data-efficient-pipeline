package sensor

import (
	"math"
	"math/rand"
	"time"
)

var lightLevels = []float64{50, 150, 300, 500, 750}

type Light struct {
	interval time.Duration
	value    float64
}

func NewLight(interval time.Duration) *Light {
	return &Light{
		interval: interval,
		value:    0,
	}
}

func (l *Light) isSleeping(now time.Time) bool {
	hour := now.Hour()
	return hour >= 23 || hour < 6
}

func (l *Light) ReadValue() Reading {
	time.Sleep(l.interval)

	now := time.Now()
	hour := float64(now.Hour()) + float64(now.Minute())/60.0

	// natural daylight: sinusoidal, peaks at noon, zero at night
	daylight := 0.0
	if hour >= 6 && hour <= 18 {
		daylight = math.Max(0, 200*math.Sin(math.Pi*(hour-6)/12))
	}

	if l.isSleeping(now) {
		l.value = rand.Float64() * 3
	} else {
		if rand.Float64() < 0.2 {
			artificial := lightLevels[rand.Intn(len(lightLevels))]
			l.value = daylight + artificial
		} else {
			artificial := math.Max(0, (l.value-daylight)+rand.NormFloat64()*5)
			l.value = daylight + artificial
		}
	}

	l.value = math.Max(0, math.Min(900, l.value))

	return Reading{
		Timestamp: now,
		Value:     l.value,
	}
}
