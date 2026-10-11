package com.membora.platform.communication;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunicationDeliveryRepository extends JpaRepository<CommunicationDelivery, UUID> {
    Optional<CommunicationDelivery> findByFirebaseUserIdAndDeliveryKey(String firebaseUserId, String deliveryKey);
    Optional<CommunicationDelivery> findByProviderMessageId(String providerMessageId);
}
