package com.membora.platform.member;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="custom_fields", uniqueConstraints=@UniqueConstraint(name="uq_custom_field_key", columnNames={"organization_id","entity_type","field_key"}))
public class CustomField {
    @Id public UUID id; @Column(nullable=false) public UUID organizationId; @Column(nullable=false) public String entityType;
    @Column(nullable=false) public String name; @Column(nullable=false) public String fieldKey; @Column(nullable=false) public String fieldType;
    public boolean required; @Column(length=2000) public String validationRules; public int displayOrder; @Column(length=4000) public String optionsJson;
    public boolean sensitive; public boolean active;
    protected CustomField() {}
    public CustomField(UUID org, String entityType, String name, String key, String type, boolean required, String rules, int order, String options, boolean sensitive) {
        id=UUID.randomUUID(); organizationId=org; this.entityType=entityType; this.name=name; fieldKey=key; fieldType=type;
        this.required=required; validationRules=rules; displayOrder=order; optionsJson=options; this.sensitive=sensitive; active=true;
    }
}
