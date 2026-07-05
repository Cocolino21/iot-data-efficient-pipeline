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

// UKDale streams readings from a UK-DALE style .dat file: each line is
// "<unix_timestamp> <value> [extra columns...]" separated by whitespace.
// field[0] is the real timestamp, field[1] is the value (active power for mains.dat).
// The file is read line-by-line (constant memory) and loops back to the start at EOF.
type UKDale struct {
	interval time.Duration
	path     string
	file     *os.File
	scanner  *bufio.Scanner
}

// NewUKDale opens dir/file for streaming. file defaults to "mains.dat" when empty.
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
			// EOF (or error): loop back to the start of the file.
			u.file.Seek(0, io.SeekStart)
			u.scanner = bufio.NewScanner(u.file)
			if !u.scanner.Scan() {
				// Empty file: avoid a hot loop.
				return Reading{Timestamp: time.Now(), Value: 0}
			}
		}

		fields := strings.Fields(u.scanner.Text())
		if len(fields) < 2 {
			continue // skip blank/garbled lines
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
		return Reading{
			Timestamp: time.Unix(sec, nsec),
			Value:     val,
		}
	}
}
