package com.membora.platform.subscription;

public interface PlayDeveloperApi {
    PlaySubscriptionSnapshot getSubscription(String purchaseToken);
    void acknowledgeSubscription(String productId, String purchaseToken);
}
