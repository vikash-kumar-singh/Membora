CREATE TABLE users (
    id BINARY(16) PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6),
    CONSTRAINT uq_user_email UNIQUE (email)
);

CREATE TABLE roles (
    id BINARY(16) PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT fk_roles_organization FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT uq_role_org_name UNIQUE (organization_id, name)
);

CREATE TABLE role_permissions (
    role_id BINARY(16) NOT NULL,
    permission VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

CREATE TABLE organization_users (
    id BINARY(16) PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    branch_id BINARY(16),
    role_id BINARY(16) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6),
    CONSTRAINT fk_org_users_organization FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_org_users_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_org_users_role FOREIGN KEY (role_id) REFERENCES roles(id),
    CONSTRAINT uq_org_user UNIQUE (organization_id, user_id)
);

CREATE TABLE refresh_tokens (
    id BINARY(16) PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    organization_user_id BINARY(16) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6),
    replaced_by BINARY(16),
    created_at TIMESTAMP(6),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_refresh_org_user FOREIGN KEY (organization_user_id) REFERENCES organization_users(id),
    CONSTRAINT uq_refresh_hash UNIQUE (token_hash)
);
