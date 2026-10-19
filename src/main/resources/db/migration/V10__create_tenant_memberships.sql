CREATE SCHEMA IF NOT EXISTS tenancy;

CREATE TABLE tenancy.tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL CHECK (length(trim(name)) > 0)
);

CREATE TABLE tenancy.tenant_memberships (
    tenant_id UUID NOT NULL REFERENCES tenancy.tenants(id),
    oidc_subject VARCHAR(255) NOT NULL CHECK (length(trim(oidc_subject)) > 0),
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'ANALYST', 'VIEWER')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (tenant_id, oidc_subject)
);
