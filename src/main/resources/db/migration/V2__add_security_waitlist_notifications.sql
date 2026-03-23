CREATE TABLE IF NOT EXISTS app_users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    phone VARCHAR(30) NULL,
    role VARCHAR(20) NOT NULL,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    last_login_at DATETIME(6) NULL
);

SET @booking_user_id_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'user_id'
);
SET @booking_user_id_sql = IF(
    @booking_user_id_exists = 0,
    'ALTER TABLE bookings ADD COLUMN user_id BIGINT NULL AFTER tour_id',
    'SELECT 1'
);
PREPARE booking_user_id_stmt FROM @booking_user_id_sql;
EXECUTE booking_user_id_stmt;
DEALLOCATE PREPARE booking_user_id_stmt;

SET @booking_status_reason_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'status_reason'
);
SET @booking_status_reason_sql = IF(
    @booking_status_reason_exists = 0,
    'ALTER TABLE bookings ADD COLUMN status_reason VARCHAR(500) NULL AFTER status',
    'SELECT 1'
);
PREPARE booking_status_reason_stmt FROM @booking_status_reason_sql;
EXECUTE booking_status_reason_stmt;
DEALLOCATE PREPARE booking_status_reason_stmt;

SET @inquiry_user_id_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'inquiries'
      AND column_name = 'user_id'
);
SET @inquiry_user_id_sql = IF(
    @inquiry_user_id_exists = 0,
    'ALTER TABLE inquiries ADD COLUMN user_id BIGINT NULL AFTER id',
    'SELECT 1'
);
PREPARE inquiry_user_id_stmt FROM @inquiry_user_id_sql;
EXECUTE inquiry_user_id_stmt;
DEALLOCATE PREPARE inquiry_user_id_stmt;

SET @inquiry_admin_notes_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'inquiries'
      AND column_name = 'admin_notes'
);
SET @inquiry_admin_notes_sql = IF(
    @inquiry_admin_notes_exists = 0,
    'ALTER TABLE inquiries ADD COLUMN admin_notes VARCHAR(1000) NULL AFTER status',
    'SELECT 1'
);
PREPARE inquiry_admin_notes_stmt FROM @inquiry_admin_notes_sql;
EXECUTE inquiry_admin_notes_stmt;
DEALLOCATE PREPARE inquiry_admin_notes_stmt;

CREATE TABLE IF NOT EXISTS booking_activity (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    actor_user_id BIGINT NULL,
    actor_name VARCHAR(120) NOT NULL,
    actor_role VARCHAR(40) NOT NULL,
    action_type VARCHAR(80) NOT NULL,
    previous_status VARCHAR(40) NULL,
    new_status VARCHAR(40) NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_booking_activity_booking_id (booking_id)
);

CREATE TABLE IF NOT EXISTS waitlist_entries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    waitlist_reference VARCHAR(40) NOT NULL UNIQUE,
    user_id BIGINT NULL,
    tour_id VARCHAR(40) NOT NULL,
    travel_date DATE NOT NULL,
    customer_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    guests INT NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    notified_at DATETIME(6) NULL,
    booking_id BIGINT NULL,
    INDEX idx_waitlist_tour_date (tour_id, travel_date, status)
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id BIGINT NULL,
    recipient_email VARCHAR(254) NULL,
    recipient_name VARCHAR(120) NULL,
    channel VARCHAR(40) NOT NULL,
    category VARCHAR(80) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    message VARCHAR(4000) NOT NULL,
    status VARCHAR(40) NOT NULL,
    admin_visible BIT(1) NOT NULL DEFAULT b'0',
    related_booking_id BIGINT NULL,
    related_inquiry_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    failure_reason VARCHAR(500) NULL,
    INDEX idx_notifications_created_at (created_at),
    INDEX idx_notifications_recipient_email (recipient_email)
);

CREATE TABLE IF NOT EXISTS tour_view_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tour_id VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_tour_view_user_created (user_id, created_at)
);
