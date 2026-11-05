-- The name shown in the sidebar and admin tables.
-- Nullable so accounts created before this change keep working; the app falls back to the email.
ALTER TABLE users ADD COLUMN full_name VARCHAR(100);