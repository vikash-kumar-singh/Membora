package com.membora.platform.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_org_date", columnList = "organizationId,paidAt"),
    @Index(name = "idx_payment_member", columnList = "organizationId,memberId")
})
public class Payment {
    @Id public UUID id;
    @Column(nullable = false) public UUID organizationId;
    @Column(nullable = false) public UUID memberId;
    public UUID membershipId;
    @Column(nullable = false, unique = true) public String receiptNumber;
    @Column(precision = 12, scale = 2) public BigDecimal amount;
    public String method;
    public String transactionReference;
    public String status;
    public String notes;
    public UUID createdBy;
    public Instant paidAt;

    protected Payment() {}

    public Payment(UUID organizationId, UUID memberId, UUID membershipId, String receiptNumber, BigDecimal amount, String method, String reference, String notes, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.memberId = memberId;
        this.membershipId = membershipId;
        this.receiptNumber = receiptNumber;
        this.amount = amount;
        this.method = method;
        this.transactionReference = reference;
        this.status = "PAID";
        this.notes = notes;
        this.createdBy = createdBy;
        this.paidAt = Instant.now();
    }
}
