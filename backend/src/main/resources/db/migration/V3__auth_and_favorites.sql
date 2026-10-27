-- ============================================================================
-- Phase 5: user accounts and favorites.
--
-- Favorites are one polymorphic table rather than three (favorite_drivers,
-- favorite_constructors, favorite_circuits). The alternative is more type-safe
-- at the database level, but every read path wants "all of this user's
-- favorites" in one query, which would mean a three-way UNION on every load.
-- The discriminator keeps that a single indexed lookup.
--
-- The trade-off is real: entity_ref cannot carry a foreign key, because the
-- table it references depends on entity_type. Referential integrity is enforced
-- in the service layer, which validates the ref exists before inserting.
-- ============================================================================

CREATE TABLE users (
    id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         TEXT        NOT NULL,
    display_name  TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,   -- BCrypt, never the password itself
    role          TEXT        NOT NULL DEFAULT 'USER',
    enabled       BOOLEAN     NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Emails are compared case-insensitively, so the constraint has to be too:
    -- a plain UNIQUE(email) would let Alice@x.com and alice@x.com both register.
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE UNIQUE INDEX idx_users_email_lower ON users (lower(email));

CREATE TABLE user_favorites (
    id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     INTEGER     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entity_type TEXT        NOT NULL,   -- DRIVER | CONSTRUCTOR | CIRCUIT
    entity_ref  TEXT        NOT NULL,   -- driver_ref / constructor_ref / circuit_ref
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_user_favorite UNIQUE (user_id, entity_type, entity_ref),
    CONSTRAINT ck_favorite_type CHECK (entity_type IN ('DRIVER', 'CONSTRUCTOR', 'CIRCUIT'))
);

CREATE INDEX idx_favorites_user ON user_favorites (user_id);
