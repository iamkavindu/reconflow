package dev.iamkavindu.reconflow.transactions.internal.persistence;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionCandidates;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import dev.iamkavindu.reconflow.transactions.TransactionStatus;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import dev.iamkavindu.reconflow.transactions.TransactionView;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class SqlTransactionCandidates implements TransactionCandidates {

    private final JdbcTemplate jdbcTemplate;

    SqlTransactionCandidates(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionView> findByBusinessReference(TenantId tenantId, String businessReference) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(businessReference, "businessReference must not be null");
        return jdbcTemplate.query(
                "SELECT * FROM transactions.transactions WHERE tenant_id = ? AND business_reference = ? ORDER BY id",
                (row, index) -> new TransactionView(
                        new TransactionId(row.getObject("id", UUID.class)),
                        tenantId,
                        row.getString("business_reference"),
                        TransactionType.valueOf(row.getString("transaction_type")),
                        row.getBigDecimal("amount"),
                        row.getString("currency"),
                        row.getTimestamp("occurred_at").toInstant(),
                        TransactionStatus.valueOf(row.getString("status")),
                        row.getTimestamp("recorded_at").toInstant()),
                tenantId.value(),
                businessReference);
    }
}
