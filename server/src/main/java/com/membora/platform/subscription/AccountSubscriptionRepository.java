package com.membora.platform.subscription;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountSubscriptionRepository extends JpaRepository<AccountSubscription, UUID> {
    Optional<AccountSubscription> findByFirebaseUserId(String firebaseUserId);
    Optional<AccountSubscription> findByPurchaseToken(String purchaseToken);
}
