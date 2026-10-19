package dev.iamkavindu.reconflow.provider.internal.application;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.provider.ProviderTransactionListing;
import dev.iamkavindu.reconflow.provider.ProviderTransactionQueries;
import dev.iamkavindu.reconflow.provider.ProviderTransactionView;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class SqlProviderTransactionListing implements ProviderTransactionListing {
    private final JdbcClient jdbc;
    private final ProviderTransactionQueries queries;

    SqlProviderTransactionListing(JdbcClient jdbc, ProviderTransactionQueries queries) {
        this.jdbc = jdbc;
        this.queries = queries;
    }

    @Override
    public List<ProviderTransactionView> search(TenantId tenantId, Filter filter) {
        var sql = new StringBuilder("SELECT id FROM provider.provider_transactions WHERE tenant_id = ?");
        var args = new ArrayList<Object>(List.of(tenantId.value()));
        if (filter.providerTransactionId() != null) { sql.append(" AND id = ?"); args.add(filter.providerTransactionId()); }
        if (filter.providerReference() != null) { sql.append(" AND provider_transaction_reference = ?"); args.add(filter.providerReference()); }
        if (filter.reference() != null) { sql.append(" AND business_reference = ?"); args.add(filter.reference()); }
        if (filter.from() != null) { sql.append(" AND occurred_at >= ?"); args.add(Timestamp.from(filter.from())); }
        if (filter.to() != null) { sql.append(" AND occurred_at < ?"); args.add(Timestamp.from(filter.to())); }
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?");
        args.add(filter.limit()); args.add(filter.offset());
        return jdbc.sql(sql.toString()).params(args).query(UUID.class).list().stream()
                .map(id -> queries.getProviderTransaction(tenantId, new ProviderTransactionId(id))).toList();
    }
}
