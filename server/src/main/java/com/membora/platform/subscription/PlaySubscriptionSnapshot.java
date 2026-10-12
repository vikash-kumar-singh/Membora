package com.membora.platform.subscription;

import java.time.Instant;

public record PlaySubscriptionSnapshot(
    String productId,
    String basePlanId,
    String status,
    String acknowledgementState,
    String obfuscatedAccountId,
    String latestOrderId,
    Instant startedAt,
    Instant expiresAt,
    boolean autoRenewing,
    boolean testPurchase
) {
    public boolean entitledAt(Instant now) {
        boolean accessState = status.equals("SUBSCRIPTION_STATE_ACTIVE") ||
            status.equals("SUBSCRIPTION_STATE_IN_GRACE_PERIOD") ||
            status.equals("SUBSCRIPTION_STATE_CANCELED");
        return accessState && expiresAt != null && expiresAt.isAfter(now);
    }

    public boolean acknowledgementPending() {
        return "ACKNOWLEDGEMENT_STATE_PENDING".equals(acknowledgementState);
    }
}
