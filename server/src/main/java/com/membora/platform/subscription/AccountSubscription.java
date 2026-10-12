package com.membora.platform.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_subscriptions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_account_subscription_user", columnNames = "firebaseUserId"),
        @UniqueConstraint(name = "uq_account_subscription_token", columnNames = "purchaseToken")
    },
    indexes = @Index(name = "idx_account_subscription_expiry", columnList = "expiresAt"))
public class AccountSubscription {
    @Id public UUID id;
    @Column(nullable = false, length = 128) public String firebaseUserId;
    @Column(nullable = false, length = 2048) public String purchaseToken;
    @Column(nullable = false, length = 100) public String productId;
    @Column(nullable = false, length = 100) public String basePlanId;
    @Column(nullable = false, length = 60) public String status;
    @Column(length = 255) public String latestOrderId;
    public Instant startedAt;
    public Instant expiresAt;
    @Column(nullable = false) public boolean autoRenewing;
    @Column(nullable = false) public boolean acknowledged;
    @Column(nullable = false) public boolean testPurchase;
    @Column(nullable = false) public Instant lastVerifiedAt;
    @Column(nullable = false) public Instant createdAt;
    @Column(nullable = false) public Instant updatedAt;

    protected AccountSubscription() {}

    public AccountSubscription(String firebaseUserId, String purchaseToken) {
        this.id = UUID.randomUUID();
        this.firebaseUserId = firebaseUserId;
        this.purchaseToken = purchaseToken;
        this.status = "SUBSCRIPTION_STATE_PENDING";
        this.productId = "";
        this.basePlanId = "";
        this.lastVerifiedAt = Instant.EPOCH;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}
