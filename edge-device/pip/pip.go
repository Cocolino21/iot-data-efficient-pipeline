package pip

import (
	"math"
	"sync"
	"time"
)

type Point struct {
	Time  float64
	Value float64
}

type Filter struct {
	cache       []Point
	cacheSize   int
	threshold   float64
	maxInterval time.Duration
	mu          sync.Mutex
}

func NewFilter(cacheSize int, threshold float64, maxInterval time.Duration) *Filter {
	return &Filter{
		cache:       make([]Point, 0, cacheSize),
		cacheSize:   cacheSize,
		threshold:   threshold,
		maxInterval: maxInterval,
	}
}

func (f *Filter) Threshold() float64 {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.threshold
}

func (f *Filter) SetThreshold(t float64) {
	f.mu.Lock()
	f.threshold = t
	f.mu.Unlock()
}

func pipOrdering(points []Point) []int {
	n := len(points)
	order := make([]int, n)
	selected := make([]bool, n)

	selected[0] = true
	selected[n-1] = true
	order[0] = 0
	order[n-1] = 1

	for pipPos := 2; pipPos < n; pipPos++ {
		bestDist := -1.0
		bestIdx := -1

		for i := 1; i < n-1; i++ {
			if selected[i] {
				continue
			}

			left := i - 1
			for left >= 0 && !selected[left] {
				left--
			}

			right := i + 1
			for right < n && !selected[right] {
				right++
			}

			dist := verticalDistance(points[left], points[right], points[i])
			if dist > bestDist {
				bestDist = dist
				bestIdx = i
			}
		}

		selected[bestIdx] = true
		order[bestIdx] = pipPos
	}

	return order
}

func (f *Filter) Process(timestamp time.Time, value float64) bool {
	point := Point{
		Time:  float64(timestamp.UnixMilli()) / 1000.0,
		Value: value,
	}

	if len(f.cache) > 0 {
		last := f.cache[len(f.cache)-1]
		gap := time.Duration((point.Time - last.Time) * float64(time.Second))
		if gap > f.maxInterval {
			f.cache = f.cache[:0]
			f.cache = append(f.cache, point)
			return true
		}
	}

	if len(f.cache) >= f.cacheSize {
		f.cache = f.cache[1:]
	}
	f.cache = append(f.cache, point)

	if len(f.cache) < 3 {
		return true
	}

	projection := make([]Point, len(f.cache)+1)
	copy(projection, f.cache)
	clone := f.cache[len(f.cache)-1]
	clone.Time += 1.0
	projection[len(f.cache)] = clone

	order := pipOrdering(projection)

	currentIndex := len(projection) - 2
	position := order[currentIndex]
	importance := 1.0 - float64(position)/float64(len(projection))

	f.mu.Lock()
	threshold := f.threshold
	f.mu.Unlock()

	return importance > threshold
}

func verticalDistance(a, b, p Point) float64 {
	if a.Time == b.Time {
		return 0
	}
	ratio := (p.Time - a.Time) / (b.Time - a.Time)
	interpolated := a.Value + ratio*(b.Value-a.Value)
	return math.Abs(p.Value - interpolated)
}
