-- Additive migration: legacy blocks and all authorization tables are preserved.
ALTER TABLE device ADD COLUMN remote_state TEXT NOT NULL DEFAULT '{"schemaVersion":2,"schedules":[],"presets":[]}';

