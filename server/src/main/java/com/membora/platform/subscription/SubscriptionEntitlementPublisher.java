package com.membora.platform.subscription;

public interface SubscriptionEntitlementPublisher {
    void publish(AccountSubscription subscription, boolean entitled);
}
