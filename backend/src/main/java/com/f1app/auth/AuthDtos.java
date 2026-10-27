package com.f1app.auth;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response shapes for the auth endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(min = 2, max = 60) String displayName,
            @NotBlank @Size(min = 8, max = 100) String password) {
    }

    public record LoginRequest(
            @NotBlank String email,
            @NotBlank String password) {
    }

    /** expiresIn is seconds, so the client can pre-empt expiry rather than wait for a 401. */
    public record AuthResponse(
            String token,
            long expiresIn,
            UserProfile user) {
    }

    public record UserProfile(
            Integer id,
            String email,
            String displayName,
            String role) {
    }

    public record FavoriteRequest(
            @NotBlank String entityType,
            @NotBlank String entityRef) {
    }

    public record FavoriteItem(
            Integer id,
            String entityType,
            String entityRef,
            String displayName) {
    }

    public record FavoritesResponse(List<FavoriteItem> favorites) {
    }
}
