CREATE TABLE communication_deliveries (
 id BINARY(16) PRIMARY KEY,
 firebase_user_id VARCHAR(128) NOT NULL,
 delivery_key VARCHAR(180) NOT NULL,
 channel VARCHAR(20) NOT NULL,
 recipient VARCHAR(32) NOT NULL,
 template_key VARCHAR(80) NOT NULL,
 status VARCHAR(30) NOT NULL,
 provider_message_id VARCHAR(255),
 error_detail VARCHAR(1000),
 attempt_count INT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT uq_delivery_user_key UNIQUE(firebase_user_id, delivery_key),
 INDEX idx_delivery_user_created (firebase_user_id, created_at)
);
