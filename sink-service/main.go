package main

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/lib/pq"
	"github.com/segmentio/kafka-go"
)

type Observation struct {
	ThingID      string    `json:"thing_id"`
	DatastreamID string    `json:"datastream_id"`
	Timestamp    time.Time `json:"timestamp"`
	Value        float64   `json:"value"`
}

func main() {
	broker := getEnv("KAFKA_BROKER", "localhost:29092")
	topic := getEnv("KAFKA_TOPIC", "telemetry.observations")
	groupID := getEnv("KAFKA_GROUP_ID", "timescale-writer-group")
	dbURL := getEnv("DB_URL", "postgres://postgres:postgres@localhost:5432/iot?sslmode=disable")

	// Batching Configuration
	const maxBatchSize = 100000
	const maxBatchAge = 1 * time.Second

	db, err := sql.Open("postgres", dbURL)
	if err != nil {
		log.Fatalf("Failed to open DB connection: %v", err)
	}
	defer db.Close()

	if err := db.Ping(); err != nil {
		log.Fatalf("Failed to ping DB: %v", err)
	}
	log.Println("Successfully connected to TimescaleDB!")

	// Configure Kafka Reader with Explicit Commits
	r := kafka.NewReader(kafka.ReaderConfig{
		Brokers: []string{broker},
		GroupID: groupID,
		Topic:   topic,
	})
	defer r.Close()

	log.Printf("Listening to Kafka topic: %s (Batch Size: %d)", topic, maxBatchSize)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		<-sigChan
		log.Println("Shutting down safely... flushing final batch")
		cancel()
	}()

	var messageBatch []kafka.Message
	var payloadBatch []Observation
	var batchStart time.Time
	const maxFlushRetries = 3

	// Helper function to flush the buffer to the DB and commit to Kafka
	flush := func() {
		if len(messageBatch) == 0 {
			return
		}

		for attempt := 1; attempt <= maxFlushRetries; attempt++ {
			err := func() error {
				txn, err := db.Begin()
				if err != nil {
					return err
				}
				defer txn.Rollback()

				// Auto-register ALL datastream IDs in this batch to handle races
				idSet := make(map[string]struct{})
				for _, p := range payloadBatch {
					idSet[p.DatastreamID] = struct{}{}
				}
				allIDs := make([]string, 0, len(idSet))
				for id := range idSet {
					allIDs = append(allIDs, id)
				}
				if _, err := txn.Exec("SELECT ensure_datastreams($1)", pq.Array(allIDs)); err != nil {
					return err
				}

				stmt, err := txn.Prepare(pq.CopyIn("observation", "timestamp", "datastream_id", "value"))
				if err != nil {
					return err
				}

				for _, p := range payloadBatch {
					if _, err = stmt.Exec(p.Timestamp, p.DatastreamID, p.Value); err != nil {
						stmt.Close()
						return err
					}
				}

				if _, err = stmt.Exec(); err != nil {
					stmt.Close()
					return err
				}
				if err = stmt.Close(); err != nil {
					return err
				}
				return txn.Commit()
			}()

			if err == nil {
				break
			}

			if attempt < maxFlushRetries {
				log.Printf("Flush attempt %d/%d failed: %v — retrying", attempt, maxFlushRetries, err)
				time.Sleep(time.Duration(attempt) * 500 * time.Millisecond)
				continue
			}
			log.Fatalf("Flush failed after %d attempts: %v", maxFlushRetries, err)
		}

		// Commit Kafka Offsets ONLY after DB succeeds
		if err := r.CommitMessages(context.Background(), messageBatch...); err != nil {
			log.Fatalf("Failed to commit Kafka messages: %v", err)
		}

		log.Printf("Successfully flushed %d records to TimescaleDB", len(messageBatch))

		// Reset buffers
		messageBatch = messageBatch[:0]
		payloadBatch = payloadBatch[:0]
	}

	// Main Consumer Loop
	for {
		fetchCtx := ctx
		var fetchCancel context.CancelFunc = func() {}
		if len(messageBatch) > 0 {
			fetchCtx, fetchCancel = context.WithDeadline(ctx, batchStart.Add(maxBatchAge))
		}

		m, err := r.FetchMessage(fetchCtx)
		fetchCancel()

		if err != nil {
			if errors.Is(err, context.DeadlineExceeded) {
				flush()
				continue
			}
			if errors.Is(err, context.Canceled) {
				flush()
				break
			}
			log.Printf("Error fetching message: %v", err)
			continue
		}

		var payload Observation
		if err := json.Unmarshal(m.Value, &payload); err != nil {
			log.Printf("Failed to unmarshal JSON: %v. Raw: %s", err, string(m.Value))
			continue
		}

		if len(messageBatch) == 0 {
			batchStart = time.Now()
		}
		messageBatch = append(messageBatch, m)
		payloadBatch = append(payloadBatch, payload)

		if len(messageBatch) >= maxBatchSize {
			flush()
		}
	}
}

func getEnv(key, fallback string) string {
	if value, exists := os.LookupEnv(key); exists {
		return value
	}
	return fallback
}
