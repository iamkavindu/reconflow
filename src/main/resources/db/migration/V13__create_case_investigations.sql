ALTER TABLE cases.cases ADD CONSTRAINT uk_case_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE cases.investigations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    case_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED', 'RUNNING', 'COMPLETED', 'FAILED')),
    requested_by VARCHAR(255) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    result TEXT,
    failure_reason VARCHAR(500),
    model VARCHAR(200),
    prompt_version VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (tenant_id, case_id) REFERENCES cases.cases (tenant_id, id),
    CHECK ((status = 'COMPLETED') = (result IS NOT NULL)),
    CHECK ((status = 'FAILED') = (failure_reason IS NOT NULL)),
    CHECK ((status IN ('COMPLETED', 'FAILED')) = (completed_at IS NOT NULL)),
    CHECK ((status = 'REQUESTED') = (started_at IS NULL)),
    CHECK (started_at >= requested_at),
    CHECK (completed_at >= started_at)
);

CREATE UNIQUE INDEX uk_investigation_active_case ON cases.investigations (tenant_id, case_id)
    WHERE status IN ('REQUESTED', 'RUNNING');
CREATE INDEX idx_investigation_history ON cases.investigations (tenant_id, case_id, requested_at DESC, id);
