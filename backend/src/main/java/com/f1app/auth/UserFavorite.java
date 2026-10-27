package com.f1app.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_favorites")
public class UserFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** DRIVER | CONSTRUCTOR | CIRCUIT - see FavoriteType. */
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    /** The upstream natural key: driver_ref, constructor_ref or circuit_ref. */
    @Column(name = "entity_ref", nullable = false)
    private String entityRef;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected UserFavorite() {
        // for JPA
    }

    public UserFavorite(User user, FavoriteType type, String entityRef) {
        this.user = user;
        this.entityType = type.name();
        this.entityRef = entityRef;
    }

    public Integer getId() { return id; }
    public User getUser() { return user; }
    public String getEntityType() { return entityType; }
    public String getEntityRef() { return entityRef; }
    public Instant getCreatedAt() { return createdAt; }
}
