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

	// Helper function to flush the buffer to the DB and commit to Kafka
	flush := func() {
		if len(messageBatch) == 0 {
			return
		}

		// 1. Begin PostgreSQL Transaction
		txn, err := db.Begin()
		if err != nil {
			log.Fatalf("Failed to begin transaction: %v", err)
		}

		// 2. Prepare the blazing fast COPY statement
		stmt, err := txn.Prepare(pq.CopyIn("observation", "timestamp", "datastream_id", "value"))
		if err != nil {
			log.Fatalf("Failed to prepare COPY statement: %v", err)
		}

		// 3. Load all buffered data into the statement
		for _, p := range payloadBatch {
			_, err = stmt.Exec(p.Timestamp, p.DatastreamID, p.Value)
			if err != nil {
				log.Fatalf("Failed to execute COPY row: %v", err)
			}
		}

		// 4. Execute and close the statement, then commit the transaction
		if _, err = stmt.Exec(); err != nil {
			log.Fatalf("Failed to flush COPY statement: %v", err)
		}
		if err = stmt.Close(); err != nil {
			log.Fatalf("Failed to close COPY statement: %v", err)
		}
		if err = txn.Commit(); err != nil {
			log.Fatalf("Transaction commit failed: %v", err)
		}

		// 5. Commit Kafka Offsets ONLY after DB succeeds
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
		// Use a context with timeout to enforce the maxBatchAge
		fetchCtx, fetchCancel := context.WithTimeout(ctx, maxBatchAge)
		m, err := r.FetchMessage(fetchCtx)
		fetchCancel()

		if err != nil {
			// If we hit the timeout, it's time to flush whatever we have
			if errors.Is(err, context.DeadlineExceeded) {
				flush()
				continue
			}
			// If the main context was canceled (shutdown signal)
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

		// Add to buffer
		messageBatch = append(messageBatch, m)
		payloadBatch = append(payloadBatch, payload)

		// Flush if we hit the batch limit
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
