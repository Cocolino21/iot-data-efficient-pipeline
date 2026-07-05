package mqtt

import (
	"encoding/json"
	"fmt"
	"time"

	pahomqtt "github.com/eclipse/paho.mqtt.golang"
)

type Client struct {
	client pahomqtt.Client
}

type payload struct {
	Timestamp string  `json:"timestamp"`
	Value     float64 `json:"value"`
}

func NewClient(broker string, clientID string) (*Client, error) {
	opts := pahomqtt.NewClientOptions().
		AddBroker(broker).
		SetClientID(clientID)

	client := pahomqtt.NewClient(opts)
	token := client.Connect()
	token.Wait()
	if err := token.Error(); err != nil {
		return nil, fmt.Errorf("connecting to broker: %w", err)
	}

	return &Client{client: client}, nil
}

func (c *Client) Publish(prefix string, thingID string, datastreamID string, timestamp time.Time, value float64) error {
	topic := fmt.Sprintf("%s/%s/%s", prefix, thingID, datastreamID)

	data, err := json.Marshal(payload{
		Timestamp: timestamp.UTC().Format(time.RFC3339),
		Value:     value,
	})
	if err != nil {
		return fmt.Errorf("marshaling payload: %w", err)
	}

	token := c.client.Publish(topic, 0, false, data)
	token.Wait()
	return token.Error()
}

func (c *Client) Subscribe(topic string, qos byte, handler func(topic string, payload []byte)) error {
	token := c.client.Subscribe(topic, qos, func(_ pahomqtt.Client, msg pahomqtt.Message) {
		handler(msg.Topic(), msg.Payload())
	})
	token.Wait()
	return token.Error()
}

func (c *Client) Close() {
	c.client.Disconnect(250)
}
