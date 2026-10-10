package com.membora.platform.membership;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "memberships", indexes = {
    @Index(name = "idx_membership_org_member", columnList = "organizationId,memberId"),
    @Index(name = "idx_membership_org_end", columnList = "organizationId,endDate")
})
public class Membership {
    @Id public UUID id;
    @Column(nullable = false) public UUID organizationId;
    @Column(nullable = false) public UUID memberId;
    @Column(nullable = false) public UUID planId;
    public LocalDate startDate;
    public LocalDate endDate;
    @Enumerated(EnumType.STRING) public MembershipStatus status;
    @Column(precision = 12, scale = 2) public BigDecimal amount;
    @Column(precision = 12, scale = 2) public BigDecimal discount;
    @Column(precision = 12, scale = 2) public BigDecimal finalAmount;
    public String paymentStatus;
    public boolean autoRenew;
    public UUID createdBy;
    public Instant createdAt;

    protected Membership() {}

    public Membership(UUID organizationId, UUID memberId, UUID planId, LocalDate startDate, LocalDate endDate, BigDecimal amount, BigDecimal discount, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.memberId = memberId;
        this.planId = planId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = startDate.isAfter(LocalDate.now()) ? MembershipStatus.UPCOMING : MembershipStatus.ACTIVE;
        this.amount = amount;
        this.discount = discount == null ? BigDecimal.ZERO : discount;
        this.finalAmount = amount.subtract(this.discount);
        this.paymentStatus = "DUE";
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }
}
