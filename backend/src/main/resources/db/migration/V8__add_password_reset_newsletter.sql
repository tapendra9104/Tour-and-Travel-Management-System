CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(64)  NOT NULL UNIQUE,
    expires_at DATETIME(6)  NOT NULL,
    used_at    DATETIME(6)  NULL,
    created_at DATETIME(6)  NOT NULL,
    INDEX idx_prt_token (token),
    INDEX idx_prt_user_id (user_id)
);

CREATE TABLE IF NOT EXISTS newsletter_subscribers (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email          VARCHAR(254) NOT NULL UNIQUE,
    subscribed_at  DATETIME(6)  NOT NULL,
    active         BIT(1)       NOT NULL DEFAULT b'1',
    INDEX idx_newsletter_email (email)
);
