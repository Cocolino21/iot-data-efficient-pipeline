# Stage 1: Build the Go binary
FROM golang:1.26-alpine AS builder

WORKDIR /app

# Copy the module definition
COPY go.mod ./

# Copy the source code BEFORE grabbing dependencies
COPY main.go ./

# This magic command reads your main.go, finds the imports,
# downloads them, and automatically generates the missing go.sum file!
RUN go mod tidy

# Build the binary with CGO disabled
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o kafka-consumer .

# Stage 2: Create the minimal runtime container
FROM alpine:latest

RUN apk --no-cache add ca-certificates tzdata

WORKDIR /root/

# Copy the pre-built binary from the builder stage
COPY --from=builder /app/kafka-consumer .

# Run the executable
CMD ["./kafka-consumer"]