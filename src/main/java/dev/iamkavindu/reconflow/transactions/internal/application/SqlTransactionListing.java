package dev.iamkavindu.reconflow.transactions.internal.application;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import dev.iamkavindu.reconflow.transactions.TransactionListing;
import dev.iamkavindu.reconflow.transactions.TransactionQueries;
import dev.iamkavindu.reconflow.transactions.TransactionView;
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
class SqlTransactionListing implements TransactionListing {
    private final JdbcClient jdbc;
    private final TransactionQueries queries;

    SqlTransactionListing(JdbcClient jdbc, TransactionQueries queries) {
        this.jdbc = jdbc;
        this.queries = queries;
    }

    @Override
    public List<TransactionView> search(TenantId tenantId, Filter filter) {
        var sql = new StringBuilder("SELECT id FROM transactions.transactions WHERE tenant_id = ?");
        var args = new ArrayList<Object>(List.of(tenantId.value()));
        if (filter.transactionId() != null) { sql.append(" AND id = ?"); args.add(filter.transactionId()); }
        if (filter.reference() != null) { sql.append(" AND business_reference = ?"); args.add(filter.reference()); }
        if (filter.from() != null) { sql.append(" AND occurred_at >= ?"); args.add(Timestamp.from(filter.from())); }
        if (filter.to() != null) { sql.append(" AND occurred_at < ?"); args.add(Timestamp.from(filter.to())); }
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?");
        args.add(filter.limit()); args.add(filter.offset());
        return jdbc.sql(sql.toString()).params(args).query(UUID.class).list().stream()
                .map(id -> queries.getTransaction(tenantId, new TransactionId(id))).toList();
    }

    @Override
    public long count(TenantId tenantId) {
        return jdbc.sql("SELECT count(*) FROM transactions.transactions WHERE tenant_id = ?")
                .param(tenantId.value()).query(Long.class).single();
    }
}
