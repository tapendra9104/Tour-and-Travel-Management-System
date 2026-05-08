CREATE TABLE IF NOT EXISTS tours (
    id VARCHAR(120) NOT NULL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    country VARCHAR(120) NOT NULL,
    duration VARCHAR(80) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    original_price DECIMAL(10, 2) NULL,
    rating DECIMAL(3, 2) NOT NULL DEFAULT 0.00,
    reviews INT NOT NULL DEFAULT 0,
    image VARCHAR(500) NOT NULL,
    category VARCHAR(80) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    difficulty VARCHAR(40) NOT NULL,
    max_group_size INT NOT NULL DEFAULT 15,
    INDEX idx_tours_category (category),
    INDEX idx_tours_country (country)
);

CREATE TABLE IF NOT EXISTS tour_highlights (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tour_id VARCHAR(120) NOT NULL,
    highlight VARCHAR(500) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    INDEX idx_tour_highlights_tour_id (tour_id),
    CONSTRAINT fk_tour_highlights_tour FOREIGN KEY (tour_id) REFERENCES tours(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tour_included (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tour_id VARCHAR(120) NOT NULL,
    included_item VARCHAR(500) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    INDEX idx_tour_included_tour_id (tour_id),
    CONSTRAINT fk_tour_included_tour FOREIGN KEY (tour_id) REFERENCES tours(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tour_start_dates (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tour_id VARCHAR(120) NOT NULL,
    start_date DATE NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    INDEX idx_tour_start_dates_tour_id (tour_id),
    CONSTRAINT fk_tour_start_dates_tour FOREIGN KEY (tour_id) REFERENCES tours(id) ON DELETE CASCADE
);
