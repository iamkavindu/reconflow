CREATE SCHEMA IF NOT EXISTS cases;

CREATE TABLE cases.cases
(
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    reconciliation_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    notes TEXT NOT NULL,
    resolution TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_cases_tenant_reconciliation UNIQUE (tenant_id, reconciliation_id),
    CONSTRAINT chk_cases_resolution CHECK (
        (status = 'RESOLVED' AND resolution IS NOT NULL)
        OR (status IN ('OPEN', 'UNDER_REVIEW') AND resolution IS NULL)
    ),
    CONSTRAINT chk_cases_timestamps CHECK (updated_at >= opened_at)
);

CREATE INDEX idx_cases_tenant_status ON cases.cases (tenant_id, status);
