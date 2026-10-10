package com.membora.platform.communication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "communication_deliveries",
    uniqueConstraints = @UniqueConstraint(name = "uq_delivery_user_key", columnNames = {"firebaseUserId", "deliveryKey"}),
    indexes = @Index(name = "idx_delivery_user_created", columnList = "firebaseUserId,createdAt"))
public class CommunicationDelivery {
    @Id public UUID id;
    @Column(nullable = false, length = 128) public String firebaseUserId;
    @Column(nullable = false, length = 180) public String deliveryKey;
    @Column(nullable = false, length = 20) public String channel;
    @Column(nullable = false, length = 32) public String recipient;
    @Column(nullable = false, length = 80) public String templateKey;
    @Column(nullable = false, length = 30) public String status;
    @Column(length = 255) public String providerMessageId;
    @Column(length = 1000) public String errorDetail;
    @Column(nullable = false) public int attemptCount;
    @Column(nullable = false) public Instant createdAt;
    @Column(nullable = false) public Instant updatedAt;

    protected CommunicationDelivery() {}

    public CommunicationDelivery(String firebaseUserId, String deliveryKey, String channel, String recipient, String templateKey) {
        this.id = UUID.randomUUID();
        this.firebaseUserId = firebaseUserId;
        this.deliveryKey = deliveryKey;
        this.channel = channel;
        this.recipient = recipient;
        this.templateKey = templateKey;
        this.status = "PENDING";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}
