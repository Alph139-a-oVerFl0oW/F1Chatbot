-- ============================================================================
-- Phase 2: core F1 schema.
--
-- Modelled on the Ergast/Jolpica data model so ingestion is a near-direct map.
-- Every table carries the upstream string identifier (*_ref, e.g. "max_verstappen",
-- "monza") with a UNIQUE constraint: that is the natural key we upsert on, which
-- makes the backfill idempotent and safely re-runnable after a rate-limit stall.
--
-- Surrogate integer PKs are used for joins because the API refs are long strings
-- and appear in a lot of foreign keys.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Reference data
-- ---------------------------------------------------------------------------

CREATE TABLE seasons (
    year        INTEGER PRIMARY KEY,
    url         TEXT
);

CREATE TABLE circuits (
    id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    circuit_ref TEXT        NOT NULL UNIQUE,   -- Ergast circuitId, e.g. "monza"
    name        TEXT        NOT NULL,
    locality    TEXT,
    country     TEXT,
    latitude    NUMERIC(9,6),
    longitude   NUMERIC(9,6),
    url         TEXT
);

CREATE TABLE drivers (
    id               INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    driver_ref       TEXT    NOT NULL UNIQUE,  -- e.g. "max_verstappen"
    permanent_number INTEGER,                  -- null for pre-2014 drivers
    code             TEXT,                     -- e.g. "VER", null for older eras
    given_name       TEXT    NOT NULL,
    family_name      TEXT    NOT NULL,
    date_of_birth    DATE,
    nationality      TEXT,
    url              TEXT
);

CREATE TABLE constructors (
    id               INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    constructor_ref  TEXT    NOT NULL UNIQUE,  -- e.g. "red_bull"
    name             TEXT    NOT NULL,
    nationality      TEXT,
    url              TEXT
);

-- Ergast status codes: 1 = Finished, 11..19 = +N Laps, 3 = Accident, etc.
-- Upstream ids are stable and meaningful, so they are used directly as the PK.
CREATE TABLE statuses (
    id          INTEGER PRIMARY KEY,
    status      TEXT NOT NULL
);

-- ---------------------------------------------------------------------------
-- Events
-- ---------------------------------------------------------------------------

CREATE TABLE races (
    id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    season              INTEGER NOT NULL REFERENCES seasons(year),
    round               INTEGER NOT NULL,
    name                TEXT    NOT NULL,
    circuit_id          INTEGER NOT NULL REFERENCES circuits(id),
    race_date           DATE,
    race_time           TIME,                  -- UTC; null for most pre-2005 races
    url                 TEXT,

    -- Session schedule: only populated by the API from 2021 onward
    fp1_date            DATE,
    fp1_time            TIME,
    fp2_date            DATE,
    fp2_time            TIME,
    fp3_date            DATE,
    fp3_time            TIME,
    qualifying_date     DATE,
    qualifying_time     TIME,
    sprint_date         DATE,
    sprint_time         TIME,

    CONSTRAINT uq_races_season_round UNIQUE (season, round)
);

CREATE INDEX idx_races_date ON races (race_date);
CREATE INDEX idx_races_circuit ON races (circuit_id);

-- ---------------------------------------------------------------------------
-- Results
-- ---------------------------------------------------------------------------

CREATE TABLE results (
    id                INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    race_id           INTEGER NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    driver_id         INTEGER NOT NULL REFERENCES drivers(id),
    constructor_id    INTEGER NOT NULL REFERENCES constructors(id),
    car_number        INTEGER,
    grid              INTEGER,                 -- 0 = pit lane start
    position          INTEGER,                 -- null when not classified
    position_text     TEXT,                    -- "1", "R" (retired), "D", "W", "F", "N"
    position_order    INTEGER NOT NULL,        -- always populated; use for ORDER BY
    points            NUMERIC(6,2) NOT NULL DEFAULT 0,  -- .5 exists (1954 shared drives)
    laps              INTEGER,
    finish_time       TEXT,                    -- "1:32:04.567" or "+5.123"
    finish_millis     BIGINT,                  -- winner-relative; null if not classified
    fastest_lap       INTEGER,                 -- lap number of their fastest
    fastest_lap_rank  INTEGER,
    fastest_lap_time  TEXT,                    -- "1:21.046"
    fastest_lap_speed NUMERIC(8,3),            -- km/h
    status_id         INTEGER REFERENCES statuses(id),

    CONSTRAINT uq_results_race_driver UNIQUE (race_id, driver_id)
);

CREATE INDEX idx_results_driver ON results (driver_id);
CREATE INDEX idx_results_constructor ON results (constructor_id);
CREATE INDEX idx_results_race_order ON results (race_id, position_order);

-- Sprint races (2021+). Same shape, minus the fastest-lap detail the API omits.
CREATE TABLE sprint_results (
    id                INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    race_id           INTEGER NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    driver_id         INTEGER NOT NULL REFERENCES drivers(id),
    constructor_id    INTEGER NOT NULL REFERENCES constructors(id),
    car_number        INTEGER,
    grid              INTEGER,
    position          INTEGER,
    position_text     TEXT,
    position_order    INTEGER NOT NULL,
    points            NUMERIC(6,2) NOT NULL DEFAULT 0,
    laps              INTEGER,
    finish_time       TEXT,
    finish_millis     BIGINT,
    fastest_lap       INTEGER,
    fastest_lap_time  TEXT,
    status_id         INTEGER REFERENCES statuses(id),

    CONSTRAINT uq_sprint_race_driver UNIQUE (race_id, driver_id)
);

CREATE INDEX idx_sprint_driver ON sprint_results (driver_id);

CREATE TABLE qualifying (
    id             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    race_id        INTEGER NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    driver_id      INTEGER NOT NULL REFERENCES drivers(id),
    constructor_id INTEGER NOT NULL REFERENCES constructors(id),
    car_number     INTEGER,
    position       INTEGER,
    q1             TEXT,                       -- lap times kept as text: "1:21.046"
    q2             TEXT,
    q3             TEXT,

    CONSTRAINT uq_qualifying_race_driver UNIQUE (race_id, driver_id)
);

CREATE INDEX idx_qualifying_driver ON qualifying (driver_id);

-- ---------------------------------------------------------------------------
-- Standings (snapshot after each round, exactly as the API reports them)
-- ---------------------------------------------------------------------------

CREATE TABLE driver_standings (
    id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    race_id       INTEGER NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    driver_id     INTEGER NOT NULL REFERENCES drivers(id),
    points        NUMERIC(6,2) NOT NULL DEFAULT 0,
    position      INTEGER,
    position_text TEXT,                        -- "1", or "-" when unclassified
    wins          INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT uq_driver_standings UNIQUE (race_id, driver_id)
);

CREATE INDEX idx_driver_standings_driver ON driver_standings (driver_id);

CREATE TABLE constructor_standings (
    id             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    race_id        INTEGER NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    constructor_id INTEGER NOT NULL REFERENCES constructors(id),
    points         NUMERIC(6,2) NOT NULL DEFAULT 0,
    position       INTEGER,
    position_text  TEXT,
    wins           INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT uq_constructor_standings UNIQUE (race_id, constructor_id)
);

CREATE INDEX idx_constructor_standings_constructor
    ON constructor_standings (constructor_id);

-- ---------------------------------------------------------------------------
-- High-volume per-lap data.
--
-- Tables exist now so the schema is complete, but the Phase 2 backfill does NOT
-- populate them: laps and pit stops are only available per-race, which at the
-- 500 req/hour ceiling means days of crawling. Backfill these on demand, or for
-- recent seasons only, once the rest of the pipeline is proven.
-- ---------------------------------------------------------------------------

CREATE TABLE lap_times (
    race_id      INTEGER NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    driver_id    INTEGER NOT NULL REFERENCES drivers(id),
    lap          INTEGER NOT NULL,
    position     INTEGER,
    lap_time     TEXT,
    milliseconds BIGINT,

    PRIMARY KEY (race_id, driver_id, lap)
);

CREATE TABLE pit_stops (
    race_id      INTEGER NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    driver_id    INTEGER NOT NULL REFERENCES drivers(id),
    stop         INTEGER NOT NULL,
    lap          INTEGER,
    stop_time    TIME,                         -- time of day the stop happened
    duration     TEXT,                         -- "23.456"
    milliseconds BIGINT,

    PRIMARY KEY (race_id, driver_id, stop)
);

-- ---------------------------------------------------------------------------
-- Ingestion bookkeeping.
--
-- The backfill will be interrupted — by the hourly rate limit, a restart, or a
-- bad response. This table lets it resume instead of starting over, and gives
-- the scheduled updater a way to know what is already current.
-- ---------------------------------------------------------------------------

CREATE TABLE ingestion_checkpoints (
    id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resource      TEXT        NOT NULL,        -- "results", "qualifying", ...
    season        INTEGER,                     -- null for season-independent pulls
    status        TEXT        NOT NULL,        -- PENDING | IN_PROGRESS | DONE | FAILED
    records       INTEGER     NOT NULL DEFAULT 0,
    last_offset   INTEGER     NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at    TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_checkpoint UNIQUE (resource, season)
);

CREATE INDEX idx_checkpoints_status ON ingestion_checkpoints (status);
