CREATE TABLE reconciliation.reconciliations
(
    id             UUID        NOT NULL,
    tenant_id      UUID        NOT NULL,
    transaction_id UUID        NOT NULL,
    status         VARCHAR(20) NOT NULL,
    started_at     TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_reconciliations PRIMARY KEY (id),
    CONSTRAINT uk_reconciliations_tenant_transaction UNIQUE (tenant_id, transaction_id),
    CONSTRAINT chk_reconciliations_matching_window CHECK (expires_at > started_at)
);
