CREATE TABLE branches (
    id BINARY(16) PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(1000), city VARCHAR(255), state VARCHAR(255), country VARCHAR(255), timezone VARCHAR(100),
    active BOOLEAN NOT NULL, created_at TIMESTAMP(6), updated_at TIMESTAMP(6),
    CONSTRAINT fk_branches_organization FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT uq_branch_org_code UNIQUE (organization_id, code)
);

ALTER TABLE organization_users ADD CONSTRAINT fk_org_users_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
