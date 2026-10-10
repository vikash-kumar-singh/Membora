CREATE TABLE organizations (
    id BINARY(16) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    business_type VARCHAR(255),
    member_label VARCHAR(100),
    currency_code VARCHAR(10),
    timezone VARCHAR(100),
    phone VARCHAR(50),
    email VARCHAR(255),
    status VARCHAR(30),
    created_at TIMESTAMP(6)
);

CREATE TABLE members (
    id BINARY(16) PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    branch_id BINARY(16),
    member_code VARCHAR(100) NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255),
    mobile VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    city VARCHAR(255),
    status VARCHAR(30),
    joined_on DATE,
    emergency_contact VARCHAR(255),
    created_at TIMESTAMP(6),
    CONSTRAINT fk_members_organization FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT uq_member_org_code UNIQUE (organization_id, member_code),
    INDEX idx_member_org_mobile (organization_id, mobile)
);

CREATE TABLE membership_plans (
    id BINARY(16) PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    duration_value INT NOT NULL,
    duration_unit VARCHAR(30),
    price DECIMAL(12,2),
    registration_fee DECIMAL(12,2),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6),
    CONSTRAINT fk_plans_organization FOREIGN KEY (organization_id) REFERENCES organizations(id),
    INDEX idx_plan_org (organization_id)
);

CREATE TABLE memberships (
    id BINARY(16) PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    member_id BINARY(16) NOT NULL,
    plan_id BINARY(16) NOT NULL,
    start_date DATE,
    end_date DATE,
    status ENUM('UPCOMING','ACTIVE','EXPIRING_SOON','EXPIRED','SUSPENDED','CANCELLED','FROZEN'),
    amount DECIMAL(12,2),
    discount DECIMAL(12,2),
    final_amount DECIMAL(12,2),
    payment_status VARCHAR(30),
    auto_renew BOOLEAN NOT NULL,
    created_by BINARY(16),
    created_at TIMESTAMP(6),
    CONSTRAINT fk_memberships_organization FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_memberships_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_memberships_plan FOREIGN KEY (plan_id) REFERENCES membership_plans(id),
    INDEX idx_membership_org_member (organization_id, member_id),
    INDEX idx_membership_org_end (organization_id, end_date)
);

CREATE TABLE payments (
    id BINARY(16) PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    member_id BINARY(16) NOT NULL,
    membership_id BINARY(16),
    receipt_number VARCHAR(100) NOT NULL UNIQUE,
    amount DECIMAL(12,2),
    method VARCHAR(40),
    transaction_reference VARCHAR(255),
    status VARCHAR(30),
    notes VARCHAR(1000),
    created_by BINARY(16),
    paid_at TIMESTAMP(6),
    CONSTRAINT fk_payments_organization FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_payments_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_payments_membership FOREIGN KEY (membership_id) REFERENCES memberships(id),
    INDEX idx_payment_org_date (organization_id, paid_at),
    INDEX idx_payment_member (organization_id, member_id)
);

CREATE TABLE attendance (
    id BINARY(16) PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    member_id BINARY(16) NOT NULL,
    checked_in_at TIMESTAMP(6),
    checked_out_at TIMESTAMP(6),
    method VARCHAR(40),
    CONSTRAINT fk_attendance_organization FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_attendance_member FOREIGN KEY (member_id) REFERENCES members(id),
    INDEX idx_visit_org_time (organization_id, checked_in_at)
);

CREATE TABLE services (
    id BINARY(16) PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100),
    description VARCHAR(1000),
    unit_label VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_services_organization FOREIGN KEY (organization_id) REFERENCES organizations(id)
);

CREATE TABLE custom_fields (
    id BINARY(16) PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    field_key VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    field_type VARCHAR(40) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    options_json JSON,
    CONSTRAINT fk_custom_fields_organization FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT uq_custom_field_key UNIQUE (organization_id, entity_type, field_key)
);

CREATE TABLE audit_logs (
    id BINARY(16) PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    actor_id BINARY(16),
    entity_type VARCHAR(80) NOT NULL,
    entity_id BINARY(16),
    action VARCHAR(80) NOT NULL,
    details_json JSON,
    occurred_at TIMESTAMP(6) NOT NULL,
    INDEX idx_audit_org_time (organization_id, occurred_at)
);
