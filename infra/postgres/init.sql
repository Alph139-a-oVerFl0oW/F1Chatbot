-- Runs once, on first boot of an empty pgdata volume, as the superuser.
-- Flyway repeats this in V1 so a hand-made local DB works too.
CREATE EXTENSION IF NOT EXISTS vector;
