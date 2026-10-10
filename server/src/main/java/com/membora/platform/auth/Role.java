package com.membora.platform.auth;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "roles", uniqueConstraints = @UniqueConstraint(name = "uq_role_org_name", columnNames = {"organization_id", "name"}))
public class Role {
    @Id public UUID id;
    @Column(nullable = false) public UUID organizationId;
    @Column(nullable = false) public String name;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission")
    public Set<String> permissions = new HashSet<>();
    protected Role() {}
    public Role(UUID organizationId, String name, Set<String> permissions) {
        this.id = UUID.randomUUID(); this.organizationId = organizationId; this.name = name; this.permissions.addAll(permissions);
    }
}
