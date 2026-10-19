package dev.iamkavindu.reconflow.reconciliation.internal.application;

import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationListing;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationQueries;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationView;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class SqlReconciliationListing implements ReconciliationListing {
    private final JdbcClient jdbc;
    private final ReconciliationQueries queries;

    SqlReconciliationListing(JdbcClient jdbc, ReconciliationQueries queries) {
        this.jdbc = jdbc;
        this.queries = queries;
    }

    @Override
    public List<ReconciliationView> search(TenantId tenantId, Filter filter) {
        var sql = new StringBuilder("SELECT id FROM reconciliation.reconciliations WHERE tenant_id = ?");
        var args = new ArrayList<Object>(List.of(tenantId.value()));
        if (filter.reconciliationId() != null) {
            sql.append(" AND id = ?");
            args.add(filter.reconciliationId());
        }
        if (filter.transactionId() != null) {
            sql.append(" AND transaction_id = ?");
            args.add(filter.transactionId());
        }
        if (filter.providerTransactionId() != null) {
            sql.append(
                    " AND (provider_transaction_id = ? OR provider_origin_id = ? OR exception_decision::jsonb->'providerTransactionIds' @> CAST(? AS jsonb))");
            args.add(filter.providerTransactionId());
            args.add(filter.providerTransactionId());
            args.add("[{\"value\":\"" + filter.providerTransactionId() + "\"}]");
        }
        if (filter.reference() != null) {
            sql.append(" AND business_reference = ?");
            args.add(filter.reference());
        }
        if (filter.status() != null) {
            sql.append(" AND status = ?");
            args.add(filter.status().name());
        }
        if (filter.from() != null) {
            sql.append(" AND started_at >= ?");
            args.add(Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" AND started_at < ?");
            args.add(Timestamp.from(filter.to()));
        }
        sql.append(" ORDER BY started_at DESC, id DESC LIMIT ? OFFSET ?");
        args.add(filter.limit());
        args.add(filter.offset());
        return jdbc.sql(sql.toString()).params(args).query(UUID.class).list().stream()
                .map(id -> queries.getReconciliationById(tenantId, new ReconciliationId(id)))
                .toList();
    }

    @Override
    public Map<ReconciliationStatus, Long> counts(TenantId tenantId) {
        var result = new EnumMap<ReconciliationStatus, Long>(ReconciliationStatus.class);
        jdbc.sql(
                        "SELECT status, count(*) AS total FROM reconciliation.reconciliations WHERE tenant_id = ? GROUP BY status")
                .param(tenantId.value())
                .query((rs, row) -> {
                    result.put(ReconciliationStatus.valueOf(rs.getString("status")), rs.getLong("total"));
                    return 1;
                })
                .list();
        return Map.copyOf(result);
    }
}
