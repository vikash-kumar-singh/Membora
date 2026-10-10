package com.membora.platform.organization;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class Organization {
    @Id public UUID id;
    public String name;
    public String businessType;
    public String memberLabel;
    public String currencyCode;
    public String timezone;
    public String phone;
    public String email;
    public String status;
    public Instant createdAt;

    protected Organization() {}

    public Organization(String name, String businessType, String memberLabel, String phone, String email) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.businessType = businessType;
        this.memberLabel = memberLabel;
        this.currencyCode = "INR";
        this.timezone = "Asia/Kolkata";
        this.phone = phone;
        this.email = email;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }
}
