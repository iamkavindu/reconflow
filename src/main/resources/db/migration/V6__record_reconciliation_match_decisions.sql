ALTER TABLE reconciliation.reconciliations
    ADD COLUMN provider_transaction_id UUID,
    ADD COLUMN matched_at TIMESTAMPTZ,
    ADD COLUMN rule_evaluations TEXT,
    ADD CONSTRAINT chk_reconciliations_match_decision CHECK (
        (status = 'MATCHED' AND provider_transaction_id IS NOT NULL AND matched_at IS NOT NULL AND rule_evaluations IS NOT NULL)
        OR
        (status <> 'MATCHED' AND provider_transaction_id IS NULL AND matched_at IS NULL AND rule_evaluations IS NULL)
    );

CREATE UNIQUE INDEX uk_reconciliations_tenant_provider_transaction
    ON reconciliation.reconciliations (tenant_id, provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;

CREATE INDEX idx_transactions_tenant_business_reference
    ON transactions.transactions (tenant_id, business_reference);
