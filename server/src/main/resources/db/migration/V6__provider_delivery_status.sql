ALTER TABLE communication_deliveries
    ADD COLUMN provider_status VARCHAR(40),
    ADD COLUMN provider_event_at TIMESTAMP(6),
    ADD COLUMN delivered_at TIMESTAMP(6),
    ADD COLUMN read_at TIMESTAMP(6),
    ADD COLUMN failed_at TIMESTAMP(6),
    ADD COLUMN webhook_event_count INT NOT NULL DEFAULT 0;

CREATE INDEX idx_delivery_provider_message
    ON communication_deliveries(provider_message_id);
