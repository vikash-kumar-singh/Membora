package com.membora.platform.subscription;

import com.membora.platform.media.FirebaseIdentityVerifier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Base64;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {
    private final FirebaseIdentityVerifier identities;
    private final SubscriptionService subscriptions;
    private final GooglePubSubIdentityVerifier pubSubIdentities;
    private final ObjectMapper objectMapper;
    private final String packageName;

    public SubscriptionController(
        FirebaseIdentityVerifier identities,
        SubscriptionService subscriptions,
        GooglePubSubIdentityVerifier pubSubIdentities,
        ObjectMapper objectMapper,
        @Value("${membora.play.package-name:com.membora.app}") String packageName
    ) {
        this.identities = identities;
        this.subscriptions = subscriptions;
        this.pubSubIdentities = pubSubIdentities;
        this.objectMapper = objectMapper;
        this.packageName = packageName;
    }

    @GetMapping("/status")
    ResponseEntity<SubscriptionStatusResponse> status(
        @RequestHeader(value = "X-Firebase-ID-Token", required = false) String firebaseIdToken
    ) {
        var userId = identities.requireUser(firebaseIdToken);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(subscriptions.status(userId));
    }

    @PostMapping("/verify")
    ResponseEntity<SubscriptionStatusResponse> verify(
        @RequestHeader(value = "X-Firebase-ID-Token", required = false) String firebaseIdToken,
        @Valid @RequestBody VerifySubscriptionRequest request
    ) {
        var userId = identities.requireUser(firebaseIdToken);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(subscriptions.verify(userId, request));
    }

    @PostMapping("/google-play-notifications")
    ResponseEntity<Void> googlePlayNotification(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody PubSubEnvelope envelope
    ) {
        pubSubIdentities.requireAuthorized(authorization);
        try {
            var json = new String(Base64.getDecoder().decode(envelope.message().data()));
            var notification = objectMapper.readValue(json, GooglePlayDeveloperNotification.class);
            if (!packageName.equals(notification.packageName())) {
                throw new IllegalArgumentException("Notification package does not match Membora");
            }
            if (notification.subscriptionNotification() != null &&
                notification.subscriptionNotification().purchaseToken() != null
            ) {
                subscriptions.refreshFromPlayNotification(
                    notification.subscriptionNotification().purchaseToken()
                );
            }
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Google Play notification payload is invalid");
        }
    }
}

record VerifySubscriptionRequest(
    @NotBlank @Size(max = 100) String productId,
    @NotBlank @Size(max = 2048) String purchaseToken
) {}

record SubscriptionStatusResponse(
    boolean entitled,
    String status,
    String productId,
    String basePlanId,
    Instant expiresAt,
    boolean autoRenewing,
    Instant verifiedAt
) {
    static SubscriptionStatusResponse none() {
        return new SubscriptionStatusResponse(false, "NONE", "", "", null, false, null);
    }

    static SubscriptionStatusResponse from(AccountSubscription value, boolean entitled) {
        return new SubscriptionStatusResponse(
            entitled,
            value.status,
            value.productId,
            value.basePlanId,
            value.expiresAt,
            value.autoRenewing,
            value.lastVerifiedAt
        );
    }
}

record PubSubEnvelope(PubSubMessage message) {}
record PubSubMessage(String data, String messageId) {}
record GooglePlayDeveloperNotification(
    String packageName,
    SubscriptionNotification subscriptionNotification
) {}
record SubscriptionNotification(
    Integer notificationType,
    String purchaseToken,
    String subscriptionId
) {}
