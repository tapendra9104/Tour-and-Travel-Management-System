CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    transaction_reference VARCHAR(60) NOT NULL UNIQUE,
    receipt_number VARCHAR(60) NOT NULL UNIQUE,
    provider_name VARCHAR(120) NOT NULL,
    provider_reference VARCHAR(120) NOT NULL,
    stage VARCHAR(20) NOT NULL,
    method VARCHAR(30) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    note VARCHAR(500) NULL,
    actor_user_id BIGINT NULL,
    actor_name VARCHAR(120) NOT NULL,
    actor_role VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_payment_transactions_booking_created (booking_id, created_at),
    INDEX idx_payment_transactions_created_at (created_at)
);
