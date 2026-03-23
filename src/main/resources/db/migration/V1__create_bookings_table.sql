CREATE TABLE bookings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_reference VARCHAR(40) NOT NULL,
    tour_id VARCHAR(40) NOT NULL,
    customer_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    guests INT NOT NULL,
    travel_date DATE NOT NULL,
    service_fee DECIMAL(10, 2) NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_bookings_reference (booking_reference),
    INDEX idx_bookings_created_at (created_at),
    INDEX idx_bookings_status (status),
    INDEX idx_bookings_tour_id (tour_id)
);
