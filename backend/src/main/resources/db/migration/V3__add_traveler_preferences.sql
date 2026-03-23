SET @booking_meal_preference_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'meal_preference'
);
SET @booking_meal_preference_sql = IF(
    @booking_meal_preference_exists = 0,
    'ALTER TABLE bookings ADD COLUMN meal_preference VARCHAR(80) NULL AFTER total_price',
    'SELECT 1'
);
PREPARE booking_meal_preference_stmt FROM @booking_meal_preference_sql;
EXECUTE booking_meal_preference_stmt;
DEALLOCATE PREPARE booking_meal_preference_stmt;

SET @booking_assistance_notes_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'assistance_notes'
);
SET @booking_assistance_notes_sql = IF(
    @booking_assistance_notes_exists = 0,
    'ALTER TABLE bookings ADD COLUMN assistance_notes VARCHAR(500) NULL AFTER meal_preference',
    'SELECT 1'
);
PREPARE booking_assistance_notes_stmt FROM @booking_assistance_notes_sql;
EXECUTE booking_assistance_notes_stmt;
DEALLOCATE PREPARE booking_assistance_notes_stmt;

SET @booking_transfer_required_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'transfer_required'
);
SET @booking_transfer_required_sql = IF(
    @booking_transfer_required_exists = 0,
    'ALTER TABLE bookings ADD COLUMN transfer_required BIT(1) NOT NULL DEFAULT b''0'' AFTER assistance_notes',
    'SELECT 1'
);
PREPARE booking_transfer_required_stmt FROM @booking_transfer_required_sql;
EXECUTE booking_transfer_required_stmt;
DEALLOCATE PREPARE booking_transfer_required_stmt;

SET @booking_traveler_notes_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'traveler_notes'
);
SET @booking_traveler_notes_sql = IF(
    @booking_traveler_notes_exists = 0,
    'ALTER TABLE bookings ADD COLUMN traveler_notes VARCHAR(500) NULL AFTER transfer_required',
    'SELECT 1'
);
PREPARE booking_traveler_notes_stmt FROM @booking_traveler_notes_sql;
EXECUTE booking_traveler_notes_stmt;
DEALLOCATE PREPARE booking_traveler_notes_stmt;
