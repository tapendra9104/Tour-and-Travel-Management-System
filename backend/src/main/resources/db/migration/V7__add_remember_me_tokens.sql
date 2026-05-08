CREATE TABLE IF NOT EXISTS persistent_logins (
    username  VARCHAR(254) NOT NULL,
    series    VARCHAR(64)  NOT NULL PRIMARY KEY,
    token     VARCHAR(64)  NOT NULL,
    last_used TIMESTAMP   NOT NULL,
    INDEX idx_persistent_logins_username (username)
);
