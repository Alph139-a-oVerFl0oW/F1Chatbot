package com.f1app.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and validates JSON Web Tokens.
 *
 * DESIGN NOTES, because these are the questions people ask:
 *
 * - Access token only, no refresh token. A refresh flow means a second token
 *   type, a revocation store, and rotation logic. For this application the
 *   trade-off is a 24-hour expiry: long enough not to annoy, short enough that
 *   a leaked token ages out. Real revocation would need server-side state,
 *   which is exactly what stateless auth trades away.
 *
 * - The subject is the user id, not the email. Emails can change; the id can't.
 *
 * - The signing key must be at least 256 bits for HS256. The default below is
 *   development-only and the app logs a warning if it is still in use.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    static final String DEV_SECRET =
            "dev-only-insecure-signing-key-change-me-in-production-0123456789";

    private final SecretKey key;
    private final Duration expiry;

    public JwtService(@Value("${app.jwt.secret:" + DEV_SECRET + "}") String secret,
                      @Value("${app.jwt.expiry-hours:24}") long expiryHours) {
        if (DEV_SECRET.equals(secret)) {
            log.warn("Using the built-in development JWT secret. Set APP_JWT_SECRET "
                    + "before deploying anywhere real.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiry = Duration.ofHours(expiryHours);
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiry)))
                .signWith(key)
                .compact();
    }

    /**
     * Returns the user id if the token is valid, or null if it is malformed,
     * expired, or signed with the wrong key. Callers treat null as anonymous
     * rather than as an error: an invalid token on a public endpoint should
     * not produce a 500.
     */
    public Integer parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Integer.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected token: {}", e.getMessage());
            return null;
        }
    }

    public long expirySeconds() {
        return expiry.toSeconds();
    }
}
