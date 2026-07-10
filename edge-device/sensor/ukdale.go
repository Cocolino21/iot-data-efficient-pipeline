package sensor

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

const replayYear = 2026

type UKDale struct {
	interval time.Duration
	path     string
	file     *os.File
	scanner  *bufio.Scanner
}

func NewUKDale(interval time.Duration, dir, file string) (*UKDale, error) {
	if file == "" {
		file = "mains.dat"
	}
	path := filepath.Join(dir, file)
	f, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("opening dataset %q: %w", path, err)
	}
	return &UKDale{
		interval: interval,
		path:     path,
		file:     f,
		scanner:  bufio.NewScanner(f),
	}, nil
}

func (u *UKDale) ReadValue() Reading {
	time.Sleep(u.interval)

	for {
		if !u.scanner.Scan() {
			u.file.Seek(0, io.SeekStart)
			u.scanner = bufio.NewScanner(u.file)
			if !u.scanner.Scan() {
				// Empty file: avoid a hot loop.
				return Reading{Timestamp: time.Now(), Value: 0}
			}
		}

		fields := strings.Fields(u.scanner.Text())
		if len(fields) < 2 {
			continue
		}

		tsFloat, err := strconv.ParseFloat(fields[0], 64)
		if err != nil {
			continue
		}
		val, err := strconv.ParseFloat(fields[1], 64)
		if err != nil {
			continue
		}

		sec := int64(tsFloat)
		nsec := int64((tsFloat - float64(sec)) * 1e9)
		ts := time.Unix(sec, nsec).UTC()
		ts = time.Date(replayYear, ts.Month(), ts.Day(), ts.Hour(), ts.Minute(), ts.Second(), ts.Nanosecond(), time.UTC)
		return Reading{
			Timestamp: ts,
			Value:     val,
		}
	}
}
