package com.membora.platform.subscription;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class GooglePlayDeveloperApi implements PlayDeveloperApi {
    private final GoogleAccessTokenProvider tokens;
    private final RestClient client;
    private final String packageName;

    public GooglePlayDeveloperApi(
        GoogleAccessTokenProvider tokens,
        @Value("${membora.play.package-name:com.membora.app}") String packageName
    ) {
        this.tokens = tokens;
        this.packageName = packageName;
        this.client = RestClient.builder()
            .baseUrl("https://androidpublisher.googleapis.com")
            .build();
    }

    @Override
    public PlaySubscriptionSnapshot getSubscription(String purchaseToken) {
        try {
            var response = client.get()
                .uri("/androidpublisher/v3/applications/{package}/purchases/subscriptionsv2/tokens/{token}",
                    packageName, purchaseToken)
                .headers(headers -> headers.setBearerAuth(tokens.accessToken()))
                .retrieve()
                .body(SubscriptionPurchaseV2.class);
            if (response == null || response.lineItems() == null || response.lineItems().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google Play returned an incomplete subscription");
            }
            var line = response.lineItems().stream()
                .filter(item -> item.expiryTime() != null)
                .max((first, second) -> Instant.parse(first.expiryTime()).compareTo(Instant.parse(second.expiryTime())))
                .orElse(response.lineItems().get(0));
            return new PlaySubscriptionSnapshot(
                line.productId(),
                line.offerDetails() == null ? "" : line.offerDetails().basePlanId(),
                value(response.subscriptionState()),
                value(response.acknowledgementState()),
                response.externalAccountIdentifiers() == null
                    ? "" : value(response.externalAccountIdentifiers().obfuscatedExternalAccountId()),
                value(line.latestSuccessfulOrderId()),
                response.startTime() == null ? null : Instant.parse(response.startTime()),
                line.expiryTime() == null ? null : Instant.parse(line.expiryTime()),
                line.autoRenewingPlan() != null && line.autoRenewingPlan().autoRenewEnabled(),
                response.testPurchase() != null
            );
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                exception.getStatusCode(),
                "Google Play could not verify this subscription",
                exception
            );
        }
    }

    @Override
    public void acknowledgeSubscription(String productId, String purchaseToken) {
        try {
            client.post()
                .uri("/androidpublisher/v3/applications/{package}/purchases/subscriptions/{product}/tokens/{token}:acknowledge",
                    packageName, productId, purchaseToken)
                .headers(headers -> headers.setBearerAuth(tokens.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                exception.getStatusCode(),
                "Google Play could not acknowledge this subscription",
                exception
            );
        }
    }

    private static String value(String value) { return value == null ? "" : value; }

    record SubscriptionPurchaseV2(
        String startTime,
        String subscriptionState,
        String acknowledgementState,
        ExternalAccountIdentifiers externalAccountIdentifiers,
        Map<String, Object> testPurchase,
        List<LineItem> lineItems
    ) {}

    record ExternalAccountIdentifiers(String obfuscatedExternalAccountId) {}
    record LineItem(
        String productId,
        String expiryTime,
        String latestSuccessfulOrderId,
        AutoRenewingPlan autoRenewingPlan,
        OfferDetails offerDetails
    ) {}
    record AutoRenewingPlan(boolean autoRenewEnabled) {}
    record OfferDetails(String basePlanId) {}
}
