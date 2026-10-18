package dev.iamkavindu.reconflow.provider.internal.persistence;

import dev.iamkavindu.reconflow.provider.ProviderTransactionCandidates;
import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import dev.iamkavindu.reconflow.provider.ProviderTransactionView;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class SqlProviderTransactionCandidates implements ProviderTransactionCandidates {

    private final JdbcTemplate jdbcTemplate;

    SqlProviderTransactionCandidates(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderTransactionView> findByBusinessReference(TenantId tenantId, String businessReference) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(businessReference, "businessReference must not be null");
        return jdbcTemplate.query(
                "SELECT * FROM provider.provider_transactions WHERE tenant_id = ? AND business_reference = ? ORDER BY id",
                (row, index) -> new ProviderTransactionView(
                        new ProviderTransactionId(row.getObject("id", UUID.class)),
                        tenantId,
                        row.getString("provider_event_id"),
                        row.getString("provider"),
                        row.getString("provider_transaction_reference"),
                        row.getString("business_reference"),
                        ProviderTransactionType.valueOf(row.getString("transaction_type")),
                        row.getBigDecimal("amount"),
                        row.getString("currency"),
                        ProviderTransactionStatus.valueOf(row.getString("status")),
                        row.getTimestamp("occurred_at").toInstant(),
                        row.getTimestamp("recorded_at").toInstant()),
                tenantId.value(),
                businessReference);
    }
}
