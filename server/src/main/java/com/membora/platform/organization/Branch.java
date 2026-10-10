package com.membora.platform.organization;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branches", uniqueConstraints = @UniqueConstraint(name = "uq_branch_org_code", columnNames = {"organization_id", "code"}))
public class Branch {
    @Id public UUID id;
    @Column(nullable = false) public UUID organizationId;
    @Column(nullable = false) public String code;
    @Column(nullable = false) public String name;
    public String address; public String city; public String state; public String country; public String timezone;
    public boolean active; public Instant createdAt; public Instant updatedAt;
    protected Branch() {}
    public Branch(UUID organizationId, String code, String name, String address, String city, String state, String country, String timezone) {
        this.id = UUID.randomUUID(); this.organizationId = organizationId; this.code = code; this.name = name; this.address = address;
        this.city = city; this.state = state; this.country = country; this.timezone = timezone; this.active = true;
        this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
}
