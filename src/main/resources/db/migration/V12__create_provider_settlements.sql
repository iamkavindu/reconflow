CREATE TABLE provider.settlements (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_settlement_reference VARCHAR(200) NOT NULL,
    currency CHAR(3) NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    fee_amount NUMERIC(19,4) NOT NULL CHECK (fee_amount >= 0),
    net_amount NUMERIC(19,4) NOT NULL,
    settled_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    lines TEXT NOT NULL,
    CONSTRAINT uk_settlement_source UNIQUE (tenant_id, provider, provider_settlement_reference),
    CONSTRAINT chk_settlement_totals CHECK (gross_amount - fee_amount = net_amount)
);

CREATE INDEX idx_settlement_lines ON provider.settlements USING GIN ((lines::jsonb));
