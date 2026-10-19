package dev.iamkavindu.reconflow.cases.internal.application;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.CaseListing;
import dev.iamkavindu.reconflow.cases.CaseQueries;
import dev.iamkavindu.reconflow.cases.CaseStatus;
import dev.iamkavindu.reconflow.cases.CaseView;
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
class SqlCaseListing implements CaseListing {
    private final JdbcClient jdbc;
    private final CaseQueries queries;

    SqlCaseListing(JdbcClient jdbc, CaseQueries queries) {
        this.jdbc = jdbc;
        this.queries = queries;
    }

    @Override
    public List<CaseView> search(TenantId tenantId, Filter filter) {
        var sql = new StringBuilder("SELECT id FROM cases.cases WHERE tenant_id = ?");
        var args = new ArrayList<Object>(List.of(tenantId.value()));
        if (filter.caseId() != null) {
            sql.append(" AND id = ?");
            args.add(filter.caseId());
        }
        if (filter.reconciliationId() != null) {
            sql.append(" AND reconciliation_id = ?");
            args.add(filter.reconciliationId());
        }
        if (filter.status() != null) {
            sql.append(" AND status = ?");
            args.add(filter.status().name());
        }
        if (filter.from() != null) {
            sql.append(" AND opened_at >= ?");
            args.add(Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" AND opened_at < ?");
            args.add(Timestamp.from(filter.to()));
        }
        sql.append(" ORDER BY opened_at DESC, id DESC LIMIT ? OFFSET ?");
        args.add(filter.limit());
        args.add(filter.offset());
        return jdbc.sql(sql.toString()).params(args).query(UUID.class).list().stream()
                .map(id -> queries.getCase(tenantId, new CaseId(id)))
                .toList();
    }

    @Override
    public Map<CaseStatus, Long> counts(TenantId tenantId) {
        var result = new EnumMap<CaseStatus, Long>(CaseStatus.class);
        jdbc.sql("SELECT status, count(*) AS total FROM cases.cases WHERE tenant_id = ? GROUP BY status")
                .param(tenantId.value())
                .query((rs, row) -> {
                    result.put(CaseStatus.valueOf(rs.getString("status")), rs.getLong("total"));
                    return 1;
                })
                .list();
        return Map.copyOf(result);
    }
}
