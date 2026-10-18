CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE audit.audit_entries (
    sequence BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    resource_id UUID NOT NULL,
    action VARCHAR(60) NOT NULL,
    actor VARCHAR(255),
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID,
    causation_id UUID,
    CONSTRAINT uk_audit_tenant_event UNIQUE (tenant_id, event_id)
);

CREATE INDEX idx_audit_resource_history
    ON audit.audit_entries (tenant_id, resource_type, resource_id, sequence DESC);

CREATE FUNCTION audit.reject_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Audit history is append-only';
END;
$$;

CREATE TRIGGER audit_history_immutable
    BEFORE UPDATE OR DELETE OR TRUNCATE ON audit.audit_entries
    FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_history_mutation();
