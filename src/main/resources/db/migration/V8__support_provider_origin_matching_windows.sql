ALTER TABLE reconciliation.reconciliations
    ALTER COLUMN transaction_id DROP NOT NULL,
    ADD COLUMN provider_origin_id UUID,
    ADD COLUMN business_reference VARCHAR(100);

UPDATE reconciliation.reconciliations r
SET business_reference = t.business_reference
FROM transactions.transactions t
WHERE t.tenant_id = r.tenant_id AND t.id = r.transaction_id;

ALTER TABLE reconciliation.reconciliations
    ALTER COLUMN business_reference SET NOT NULL,
    ADD CONSTRAINT uk_reconciliations_tenant_provider_origin UNIQUE (tenant_id, provider_origin_id),
    ADD CONSTRAINT chk_reconciliations_source CHECK (transaction_id IS NOT NULL OR provider_origin_id IS NOT NULL);

CREATE INDEX idx_reconciliations_pending_expiry
    ON reconciliation.reconciliations (expires_at, id) WHERE status = 'PENDING';
