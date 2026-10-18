CREATE SCHEMA IF NOT EXISTS provider;

CREATE TABLE provider.provider_transactions
(
    id                             UUID           NOT NULL,
    tenant_id                      UUID           NOT NULL,
    provider_event_id              VARCHAR(200)   NOT NULL,
    provider                       VARCHAR(50)    NOT NULL,
    provider_transaction_reference VARCHAR(200)   NOT NULL,
    business_reference             VARCHAR(100)   NOT NULL,
    transaction_type               VARCHAR(20)    NOT NULL,
    amount                         NUMERIC(19, 4) NOT NULL,
    currency                       VARCHAR(3)     NOT NULL,
    status                         VARCHAR(20)    NOT NULL,
    occurred_at                    TIMESTAMPTZ    NOT NULL,
    recorded_at                    TIMESTAMPTZ    NOT NULL,
    correlation_id                 UUID           NOT NULL,
    causation_id                   UUID           NOT NULL,
    CONSTRAINT pk_provider_transactions PRIMARY KEY (id),
    CONSTRAINT uk_provider_transactions_event UNIQUE (tenant_id, provider, provider_event_id),
    CONSTRAINT uk_provider_transactions_reference UNIQUE (tenant_id, provider, provider_transaction_reference),
    CONSTRAINT chk_provider_transactions_positive_amount CHECK (amount > 0)
);

CREATE INDEX idx_provider_transactions_tenant_business_reference
    ON provider.provider_transactions (tenant_id, business_reference);
