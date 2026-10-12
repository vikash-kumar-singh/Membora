package com.membora.platform.subscription;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {
    private static final Set<String> ALLOWED_BASE_PLANS = Set.of(
        "monthly", "three-month", "six-month", "annual"
    );
    private static final Duration MAX_CACHED_VERIFICATION_AGE = Duration.ofHours(24);

    private final AccountSubscriptionRepository subscriptions;
    private final PlayDeveloperApi play;
    private final SubscriptionEntitlementPublisher entitlements;
    private final Clock clock;
    private final String productId;

    @Autowired
    public SubscriptionService(
        AccountSubscriptionRepository subscriptions,
        PlayDeveloperApi play,
        SubscriptionEntitlementPublisher entitlements,
        @Value("${membora.play.product-id:membora_premium}") String productId
    ) {
        this(subscriptions, play, entitlements, Clock.systemUTC(), productId);
    }

    SubscriptionService(
        AccountSubscriptionRepository subscriptions,
        PlayDeveloperApi play,
        SubscriptionEntitlementPublisher entitlements,
        Clock clock,
        String productId
    ) {
        this.subscriptions = subscriptions;
        this.play = play;
        this.entitlements = entitlements;
        this.clock = clock;
        this.productId = productId;
    }

    @Transactional
    public SubscriptionStatusResponse verify(String firebaseUserId, VerifySubscriptionRequest request) {
        if (!productId.equals(request.productId())) {
            throw new IllegalArgumentException("Unknown Membora subscription product");
        }
        subscriptions.findByPurchaseToken(request.purchaseToken())
            .filter(existing -> !existing.firebaseUserId.equals(firebaseUserId))
            .ifPresent(existing -> {
                throw new IllegalArgumentException("This Google Play purchase belongs to another Membora account");
            });

        var snapshot = play.getSubscription(request.purchaseToken());
        validate(firebaseUserId, snapshot);
        var acknowledged = "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED".equals(snapshot.acknowledgementState());
        if (snapshot.acknowledgementPending() && snapshot.entitledAt(clock.instant())) {
            play.acknowledgeSubscription(snapshot.productId(), request.purchaseToken());
            acknowledged = true;
        }
        var subscription = subscriptions.findByFirebaseUserId(firebaseUserId)
            .orElseGet(() -> new AccountSubscription(firebaseUserId, request.purchaseToken()));
        subscription.purchaseToken = request.purchaseToken();
        update(subscription, snapshot, acknowledged);
        subscriptions.save(subscription);
        var entitled = snapshot.entitledAt(clock.instant());
        entitlements.publish(subscription, entitled);
        return SubscriptionStatusResponse.from(subscription, entitled);
    }

    @Transactional
    public SubscriptionStatusResponse status(String firebaseUserId) {
        var subscription = subscriptions.findByFirebaseUserId(firebaseUserId).orElse(null);
        if (subscription == null) return SubscriptionStatusResponse.none();
        PlaySubscriptionSnapshot snapshot;
        try {
            snapshot = play.getSubscription(subscription.purchaseToken);
        } catch (RuntimeException error) {
            var cacheFresh = subscription.lastVerifiedAt != null &&
                subscription.lastVerifiedAt.isAfter(clock.instant().minus(MAX_CACHED_VERIFICATION_AGE));
            if (!cacheFresh) throw error;
            return SubscriptionStatusResponse.from(subscription, isCachedEntitled(subscription));
        }
        validate(firebaseUserId, snapshot);
        update(
            subscription,
            snapshot,
            "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED".equals(snapshot.acknowledgementState())
        );
        subscriptions.save(subscription);
        var entitled = snapshot.entitledAt(clock.instant());
        entitlements.publish(subscription, entitled);
        return SubscriptionStatusResponse.from(subscription, entitled);
    }

    @Transactional
    public void refreshFromPlayNotification(String purchaseToken) {
        subscriptions.findByPurchaseToken(purchaseToken)
            .ifPresent(subscription -> {
                var snapshot = play.getSubscription(subscription.purchaseToken);
                validate(subscription.firebaseUserId, snapshot);
                update(
                    subscription,
                    snapshot,
                    "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED".equals(snapshot.acknowledgementState())
                );
                subscriptions.save(subscription);
                entitlements.publish(subscription, snapshot.entitledAt(clock.instant()));
            });
    }

    private void validate(String firebaseUserId, PlaySubscriptionSnapshot snapshot) {
        if (!productId.equals(snapshot.productId())) {
            throw new IllegalArgumentException("Google Play returned a different subscription product");
        }
        if (!ALLOWED_BASE_PLANS.contains(snapshot.basePlanId())) {
            throw new IllegalArgumentException("Google Play returned an unsupported billing period");
        }
        if (!obfuscatedAccountId(firebaseUserId).equals(snapshot.obfuscatedAccountId())) {
            throw new IllegalArgumentException("This purchase is not linked to the signed-in Membora account");
        }
    }

    private void update(
        AccountSubscription subscription,
        PlaySubscriptionSnapshot snapshot,
        boolean acknowledged
    ) {
        var now = clock.instant();
        subscription.productId = snapshot.productId();
        subscription.basePlanId = snapshot.basePlanId();
        subscription.status = snapshot.status();
        subscription.latestOrderId = snapshot.latestOrderId();
        subscription.startedAt = snapshot.startedAt();
        subscription.expiresAt = snapshot.expiresAt();
        subscription.autoRenewing = snapshot.autoRenewing();
        subscription.acknowledged = acknowledged;
        subscription.testPurchase = snapshot.testPurchase();
        subscription.lastVerifiedAt = now;
        subscription.updatedAt = now;
    }

    private boolean isCachedEntitled(AccountSubscription subscription) {
        return subscription.expiresAt != null && subscription.expiresAt.isAfter(clock.instant()) &&
            Set.of(
                "SUBSCRIPTION_STATE_ACTIVE",
                "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
                "SUBSCRIPTION_STATE_CANCELED"
            ).contains(subscription.status);
    }

    static String obfuscatedAccountId(String firebaseUserId) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(firebaseUserId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
