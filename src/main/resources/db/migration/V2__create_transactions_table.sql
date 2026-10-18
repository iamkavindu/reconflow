CREATE TABLE transactions.transactions
(
    id                 UUID           NOT NULL,
    tenant_id          UUID           NOT NULL,
    business_reference VARCHAR(100)   NOT NULL,
    transaction_type   VARCHAR(20)    NOT NULL,
    amount             NUMERIC(19, 4) NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    occurred_at        TIMESTAMPTZ    NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    recorded_at        TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT chk_transactions_positive_amount CHECK (amount > 0)
);

CREATE INDEX idx_transactions_tenant_id_id
    ON transactions.transactions (tenant_id, id);
