package com.membora.platform.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization_users", uniqueConstraints = @UniqueConstraint(name = "uq_org_user", columnNames = {"organization_id", "user_id"}))
public class OrganizationUser {
    @Id public UUID id;
    @Column(nullable = false) public UUID organizationId;
    @Column(nullable = false) public UUID userId;
    public UUID branchId;
    @Column(nullable = false) public UUID roleId;
    @Column(nullable = false) public String status;
    public Instant createdAt;
    protected OrganizationUser() {}
    public OrganizationUser(UUID organizationId, UUID userId, UUID branchId, UUID roleId) {
        this.id = UUID.randomUUID(); this.organizationId = organizationId; this.userId = userId; this.branchId = branchId;
        this.roleId = roleId; this.status = "ACTIVE"; this.createdAt = Instant.now();
    }
}
