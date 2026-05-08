-- V9: Add performance indexes for high-traffic queries.
-- Replaces the mistakenly duplicated V0 content (tours tables already created in V0).
-- All indexes use IF NOT EXISTS-style guards via CREATE INDEX ... (MySQL silently no-ops
-- if the index already exists when using the same name, so this migration is idempotent).

-- Bookings: most-used lookup patterns
ALTER TABLE bookings ADD INDEX IF NOT EXISTS idx_bookings_email (email);
ALTER TABLE bookings ADD INDEX IF NOT EXISTS idx_bookings_status (status);
ALTER TABLE bookings ADD INDEX IF NOT EXISTS idx_bookings_tour_id (tour_id);
ALTER TABLE bookings ADD INDEX IF NOT EXISTS idx_bookings_date (date);
ALTER TABLE bookings ADD INDEX IF NOT EXISTS idx_bookings_created_at (created_at);

-- Inquiries: admin filtering
ALTER TABLE inquiries ADD INDEX IF NOT EXISTS idx_inquiries_status (status);
ALTER TABLE inquiries ADD INDEX IF NOT EXISTS idx_inquiries_created_at (created_at);

-- Waitlist: tour + status lookup
ALTER TABLE waitlist_entries ADD INDEX IF NOT EXISTS idx_waitlist_tour_status (tour_id, status);
ALTER TABLE waitlist_entries ADD INDEX IF NOT EXISTS idx_waitlist_created_at (created_at);

-- Tour view events: analytics queries
ALTER TABLE tour_view_events ADD INDEX IF NOT EXISTS idx_tour_views_user_tour (user_id, tour_id);
ALTER TABLE tour_view_events ADD INDEX IF NOT EXISTS idx_tour_views_viewed_at (viewed_at);

-- Notifications: unread fetch for logged-in users
ALTER TABLE notifications ADD INDEX IF NOT EXISTS idx_notifications_user_status (user_id, status);
ALTER TABLE notifications ADD INDEX IF NOT EXISTS idx_notifications_created_at (created_at);
