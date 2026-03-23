SET @booking_transport_mode_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'transport_mode'
);
SET @booking_transport_mode_sql = IF(
    @booking_transport_mode_exists = 0,
    'ALTER TABLE bookings ADD COLUMN transport_mode VARCHAR(40) NULL AFTER traveler_notes',
    'SELECT 1'
);
PREPARE booking_transport_mode_stmt FROM @booking_transport_mode_sql;
EXECUTE booking_transport_mode_stmt;
DEALLOCATE PREPARE booking_transport_mode_stmt;

SET @booking_transport_class_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'transport_class'
);
SET @booking_transport_class_sql = IF(
    @booking_transport_class_exists = 0,
    'ALTER TABLE bookings ADD COLUMN transport_class VARCHAR(80) NULL AFTER transport_mode',
    'SELECT 1'
);
PREPARE booking_transport_class_stmt FROM @booking_transport_class_sql;
EXECUTE booking_transport_class_stmt;
DEALLOCATE PREPARE booking_transport_class_stmt;

SET @booking_transport_status_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'transport_status'
);
SET @booking_transport_status_sql = IF(
    @booking_transport_status_exists = 0,
    'ALTER TABLE bookings ADD COLUMN transport_status VARCHAR(40) NOT NULL DEFAULT ''Not Required'' AFTER transport_class',
    'SELECT 1'
);
PREPARE booking_transport_status_stmt FROM @booking_transport_status_sql;
EXECUTE booking_transport_status_stmt;
DEALLOCATE PREPARE booking_transport_status_stmt;

SET @booking_documents_verified_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'documents_verified'
);
SET @booking_documents_verified_sql = IF(
    @booking_documents_verified_exists = 0,
    'ALTER TABLE bookings ADD COLUMN documents_verified BIT(1) NOT NULL DEFAULT b''0'' AFTER transport_status',
    'SELECT 1'
);
PREPARE booking_documents_verified_stmt FROM @booking_documents_verified_sql;
EXECUTE booking_documents_verified_stmt;
DEALLOCATE PREPARE booking_documents_verified_stmt;

SET @booking_operations_priority_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'operations_priority'
);
SET @booking_operations_priority_sql = IF(
    @booking_operations_priority_exists = 0,
    'ALTER TABLE bookings ADD COLUMN operations_priority VARCHAR(40) NOT NULL DEFAULT ''Normal'' AFTER documents_verified',
    'SELECT 1'
);
PREPARE booking_operations_priority_stmt FROM @booking_operations_priority_sql;
EXECUTE booking_operations_priority_stmt;
DEALLOCATE PREPARE booking_operations_priority_stmt;

SET @booking_operations_notes_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND column_name = 'operations_notes'
);
SET @booking_operations_notes_sql = IF(
    @booking_operations_notes_exists = 0,
    'ALTER TABLE bookings ADD COLUMN operations_notes VARCHAR(500) NULL AFTER operations_priority',
    'SELECT 1'
);
PREPARE booking_operations_notes_stmt FROM @booking_operations_notes_sql;
EXECUTE booking_operations_notes_stmt;
DEALLOCATE PREPARE booking_operations_notes_stmt;
