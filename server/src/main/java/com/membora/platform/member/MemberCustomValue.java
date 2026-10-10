package com.membora.platform.member;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="member_custom_values", uniqueConstraints=@UniqueConstraint(name="uq_member_custom_value", columnNames={"member_id","custom_field_id"}))
public class MemberCustomValue {
    @Id public UUID id; @Column(nullable=false) public UUID organizationId; @Column(nullable=false) public UUID memberId;
    @Column(nullable=false) public UUID customFieldId; @Column(length=4000) public String valueJson; public Instant updatedAt;
    protected MemberCustomValue() {}
    public MemberCustomValue(UUID org, UUID member, UUID field, String value) { id=UUID.randomUUID(); organizationId=org; memberId=member; customFieldId=field; valueJson=value; updatedAt=Instant.now(); }
}
