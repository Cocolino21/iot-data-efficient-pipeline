package sensor

import "time"

type Reading struct {
	Timestamp time.Time
	Value     float64
}

type Sensor interface {
	ReadValue() Reading
}
