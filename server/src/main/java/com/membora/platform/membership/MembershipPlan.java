package com.membora.platform.membership;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "membership_plans", indexes = @Index(name = "idx_plan_org", columnList = "organizationId"))
public class MembershipPlan {
    @Id public UUID id;
    @Column(nullable = false) public UUID organizationId;
    @Column(nullable = false) public String name;
    public String description;
    public int durationValue;
    public String durationUnit;
    @Column(precision = 12, scale = 2) public BigDecimal price;
    @Column(precision = 12, scale = 2) public BigDecimal registrationFee;
    public boolean active;
    public Instant createdAt;

    protected MembershipPlan() {}

    public MembershipPlan(UUID organizationId, String name, String description, int durationValue, String durationUnit, BigDecimal price, BigDecimal registrationFee) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.name = name;
        this.description = description;
        this.durationValue = durationValue;
        this.durationUnit = durationUnit;
        this.price = price;
        this.registrationFee = registrationFee == null ? BigDecimal.ZERO : registrationFee;
        this.active = true;
        this.createdAt = Instant.now();
    }
}
