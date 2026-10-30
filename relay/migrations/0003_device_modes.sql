-- Additive: preserve legacy blocks, schedules, presets, acknowledgements and OAuth.
ALTER TABLE device ADD COLUMN assistant_state TEXT NOT NULL DEFAULT '{"schemaVersion":1,"templates":[],"instances":[],"exceptions":[]}';
CREATE TABLE IF NOT EXISTS remote_events (
 id TEXT PRIMARY KEY,
 occurred_at INTEGER NOT NULL,
 kind TEXT NOT NULL,
 source TEXT NOT NULL,
 details TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS remote_events_time ON remote_events(occurred_at,id);
