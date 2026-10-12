package com.membora.platform.subscription;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FirestoreSubscriptionEntitlementPublisher implements SubscriptionEntitlementPublisher {
    private final GoogleAccessTokenProvider tokens;
    private final RestClient client;
    private final String projectId;

    public FirestoreSubscriptionEntitlementPublisher(
        GoogleAccessTokenProvider tokens,
        @Value("${membora.firebase.project-id}") String projectId
    ) {
        this.tokens = tokens;
        this.projectId = projectId;
        this.client = RestClient.builder().baseUrl("https://firestore.googleapis.com").build();
    }

    @Override
    public void publish(AccountSubscription subscription, boolean entitled) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("entitled", bool(entitled));
        fields.put("status", string(subscription.status));
        fields.put("productId", string(subscription.productId));
        fields.put("basePlanId", string(subscription.basePlanId));
        fields.put("autoRenewing", bool(subscription.autoRenewing));
        fields.put("acknowledged", bool(subscription.acknowledged));
        fields.put("testPurchase", bool(subscription.testPurchase));
        fields.put("verifiedAt", timestamp(subscription.lastVerifiedAt));
        if (subscription.startedAt != null) fields.put("startedAt", timestamp(subscription.startedAt));
        if (subscription.expiresAt != null) fields.put("expiresAt", timestamp(subscription.expiresAt));
        if (subscription.latestOrderId != null) fields.put("latestOrderId", string(subscription.latestOrderId));

        client.patch()
            .uri("/v1/projects/{project}/databases/(default)/documents/users/{user}/account/subscription",
                projectId, subscription.firebaseUserId)
            .headers(headers -> headers.setBearerAuth(tokens.accessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("fields", fields))
            .retrieve()
            .toBodilessEntity();
    }

    private Map<String, Object> string(String value) {
        return Map.of("stringValue", value == null ? "" : value);
    }

    private Map<String, Object> bool(boolean value) {
        return Map.of("booleanValue", value);
    }

    private Map<String, Object> timestamp(java.time.Instant value) {
        return Map.of("timestampValue", value.toString());
    }
}
