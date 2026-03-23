SET @booking_dietary_restrictions_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'dietary_restrictions'
);
SET @booking_dietary_restrictions_sql = IF(
    @booking_dietary_restrictions_exists = 0,
    'ALTER TABLE bookings ADD COLUMN dietary_restrictions VARCHAR(500) NULL AFTER meal_preference',
    'SELECT 1'
);
PREPARE booking_dietary_restrictions_stmt FROM @booking_dietary_restrictions_sql;
EXECUTE booking_dietary_restrictions_stmt;
DEALLOCATE PREPARE booking_dietary_restrictions_stmt;

SET @booking_occasion_type_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'occasion_type'
);
SET @booking_occasion_type_sql = IF(
    @booking_occasion_type_exists = 0,
    'ALTER TABLE bookings ADD COLUMN occasion_type VARCHAR(80) NULL AFTER dietary_restrictions',
    'SELECT 1'
);
PREPARE booking_occasion_type_stmt FROM @booking_occasion_type_sql;
EXECUTE booking_occasion_type_stmt;
DEALLOCATE PREPARE booking_occasion_type_stmt;

SET @booking_occasion_notes_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'occasion_notes'
);
SET @booking_occasion_notes_sql = IF(
    @booking_occasion_notes_exists = 0,
    'ALTER TABLE bookings ADD COLUMN occasion_notes VARCHAR(500) NULL AFTER occasion_type',
    'SELECT 1'
);
PREPARE booking_occasion_notes_stmt FROM @booking_occasion_notes_sql;
EXECUTE booking_occasion_notes_stmt;
DEALLOCATE PREPARE booking_occasion_notes_stmt;

SET @booking_room_preference_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'room_preference'
);
SET @booking_room_preference_sql = IF(
    @booking_room_preference_exists = 0,
    'ALTER TABLE bookings ADD COLUMN room_preference VARCHAR(120) NULL AFTER occasion_notes',
    'SELECT 1'
);
PREPARE booking_room_preference_stmt FROM @booking_room_preference_sql;
EXECUTE booking_room_preference_stmt;
DEALLOCATE PREPARE booking_room_preference_stmt;

SET @booking_trip_style_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'trip_style'
);
SET @booking_trip_style_sql = IF(
    @booking_trip_style_exists = 0,
    'ALTER TABLE bookings ADD COLUMN trip_style VARCHAR(80) NULL AFTER room_preference',
    'SELECT 1'
);
PREPARE booking_trip_style_stmt FROM @booking_trip_style_sql;
EXECUTE booking_trip_style_stmt;
DEALLOCATE PREPARE booking_trip_style_stmt;
