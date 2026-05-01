package sensor

import (
	"math/rand"
	"time"
)

type appliance struct {
	name  string
	watts float64
	pOn   float64
	pOff  float64
	state bool
}

type Power struct {
	interval   time.Duration
	appliances []appliance
	baseLoad   float64
}

func NewPower(interval time.Duration) *Power {
	return &Power{
		interval: interval,
		appliances: []appliance{
			{"fridge", 150, 0.3, 0.3, false},
			{"oven", 2000, 0.01, 0.15, false},
			{"washer", 500, 0.005, 0.05, false},
			{"TV", 120, 0.02, 0.03, false},
			{"kettle", 1800, 0.02, 0.6, false},
			{"lights", 100, 0.05, 0.05, false},
		},
		baseLoad: 80,
	}
}

func (p *Power) isSleeping(now time.Time) bool {
	hour := now.Hour()
	return hour >= 23 || hour < 6
}

func (p *Power) ReadValue() Reading {
	time.Sleep(p.interval)

	now := time.Now()
	sleeping := p.isSleeping(now)
	total := p.baseLoad

	for i := range p.appliances {
		a := &p.appliances[i]
		if sleeping && a.name != "fridge" {
			a.state = false
		} else {
			if a.state {
				if rand.Float64() < a.pOff {
					a.state = false
				}
			} else {
				if rand.Float64() < a.pOn {
					a.state = true
				}
			}
		}
		if a.state {
			total += a.watts
		}
	}

	return Reading{
		Timestamp: now,
		Value:     total,
	}
}
