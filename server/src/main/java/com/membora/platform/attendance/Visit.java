package com.membora.platform.attendance;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance", indexes = @Index(name = "idx_visit_org_time", columnList = "organizationId,checkedInAt"))
public class Visit {
    @Id public UUID id;
    @Column(nullable = false) public UUID organizationId;
    @Column(nullable = false) public UUID memberId;
    public Instant checkedInAt;
    public Instant checkedOutAt;
    public String method;

    protected Visit() {}
    public Visit(UUID organizationId, UUID memberId, String method) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.memberId = memberId;
        this.method = method;
        this.checkedInAt = Instant.now();
    }
}
