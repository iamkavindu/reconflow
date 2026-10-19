CREATE INDEX idx_transactions_tenant_occurred ON transactions.transactions (tenant_id, occurred_at DESC, id DESC);
CREATE INDEX idx_provider_transactions_tenant_occurred ON provider.provider_transactions (tenant_id, occurred_at DESC, id DESC);
CREATE INDEX idx_reconciliations_tenant_started ON reconciliation.reconciliations (tenant_id, started_at DESC, id DESC);
CREATE INDEX idx_cases_tenant_opened ON cases.cases (tenant_id, opened_at DESC, id DESC);
