package com.f1app.auth;

/**
 * Mirrors the ck_favorite_type CHECK constraint in V3. Adding a value here
 * without a migration will fail at insert time, which is the intended
 * failure mode: the database stays the source of truth.
 */
public enum FavoriteType {
    DRIVER,
    CONSTRUCTOR,
    CIRCUIT
}
