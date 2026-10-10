package com.membora.platform.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_hash", columnList = "token_hash", unique = true))
public class RefreshToken {
    @Id public UUID id;
    @Column(nullable = false) public UUID userId;
    @Column(nullable = false) public UUID organizationUserId;
    @Column(nullable = false) public String tokenHash;
    @Column(nullable = false) public Instant expiresAt;
    public Instant revokedAt;
    public UUID replacedBy;
    public Instant createdAt;
    protected RefreshToken() {}
    public RefreshToken(UUID userId, UUID organizationUserId, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID(); this.userId = userId; this.organizationUserId = organizationUserId;
        this.tokenHash = tokenHash; this.expiresAt = expiresAt; this.createdAt = Instant.now();
    }
    public boolean active() { return revokedAt == null && expiresAt.isAfter(Instant.now()); }
}
