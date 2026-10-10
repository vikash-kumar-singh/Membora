package com.membora.platform.member;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "members", indexes = {
    @Index(name = "idx_member_org_code", columnList = "organizationId,memberCode", unique = true),
    @Index(name = "idx_member_org_mobile", columnList = "organizationId,mobile")
})
public class Member {
    @Id public UUID id;
    @Column(nullable = false) public UUID organizationId;
    public UUID branchId;
    @Column(nullable = false) public String memberCode;
    @Column(nullable = false) public String firstName;
    public String lastName;
    @Column(nullable = false) public String mobile;
    public String email;
    public String city;
    public String status;
    public LocalDate joinedOn;
    public String emergencyContact;
    public Instant createdAt;

    protected Member() {}

    public Member(UUID organizationId, UUID branchId, String memberCode, String firstName, String lastName, String mobile, String email, String city, String emergencyContact) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.branchId = branchId;
        this.memberCode = memberCode;
        this.firstName = firstName;
        this.lastName = lastName;
        this.mobile = mobile;
        this.email = email;
        this.city = city;
        this.emergencyContact = emergencyContact;
        this.status = "ACTIVE";
        this.joinedOn = LocalDate.now();
        this.createdAt = Instant.now();
    }
}
