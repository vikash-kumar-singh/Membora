ALTER TABLE custom_fields ADD COLUMN validation_rules VARCHAR(2000), ADD COLUMN display_order INT NOT NULL DEFAULT 0,
    ADD COLUMN sensitive BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    CHANGE COLUMN options_json options_json VARCHAR(4000);
CREATE TABLE member_custom_values (
 id BINARY(16) PRIMARY KEY, organization_id BINARY(16) NOT NULL, member_id BINARY(16) NOT NULL, custom_field_id BINARY(16) NOT NULL,
 value_json VARCHAR(4000), updated_at TIMESTAMP(6),
 CONSTRAINT fk_custom_value_org FOREIGN KEY(organization_id) REFERENCES organizations(id),
 CONSTRAINT fk_custom_value_member FOREIGN KEY(member_id) REFERENCES members(id),
 CONSTRAINT fk_custom_value_field FOREIGN KEY(custom_field_id) REFERENCES custom_fields(id),
 CONSTRAINT uq_member_custom_value UNIQUE(member_id,custom_field_id)
);
