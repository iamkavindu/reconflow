package dev.iamkavindu.reconflow.provider.internal.persistence;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.provider.ProviderTransactionQueries;
import dev.iamkavindu.reconflow.provider.SettlementId;
import dev.iamkavindu.reconflow.provider.SettlementLine;
import dev.iamkavindu.reconflow.provider.SettlementQueries;
import dev.iamkavindu.reconflow.provider.SettlementView;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class SqlSettlementQueries implements SettlementQueries {

    private final JdbcClient jdbc;
    private final ProviderTransactionQueries transactions;
    private final JsonMapper json;

    public SqlSettlementQueries(JdbcClient jdbc, ProviderTransactionQueries transactions, JsonMapper json) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public SettlementView getSettlement(TenantId tenantId, SettlementId settlementId) {
        return jdbc.sql("SELECT * FROM provider.settlements WHERE tenant_id = ? AND id = ?")
                .params(tenantId.value(), settlementId.value())
                .query(this::map)
                .optional()
                .orElseThrow(() -> new NoSuchElementException("Settlement not found"));
    }

    @Transactional(readOnly = true)
    public SettlementView forSource(TenantId tenantId, String provider, String reference) {
        return jdbc.sql("""
                        SELECT * FROM provider.settlements
                        WHERE tenant_id = ? AND provider = ? AND provider_settlement_reference = ?
                        """)
                .params(tenantId.value(), provider, reference)
                .query(this::map)
                .optional()
                .orElseThrow(() -> new NoSuchElementException("Settlement not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettlementView> forProviderTransaction(TenantId tenantId, ProviderTransactionId providerTransactionId) {
        var transaction = transactions.getProviderTransaction(tenantId, providerTransactionId);
        String reference = json.writeValueAsString(
                List.of(Map.of("providerTransactionReference", transaction.providerTransactionReference())));
        return jdbc.sql("""
                        SELECT * FROM provider.settlements
                        WHERE tenant_id = ? AND provider = ? AND lines::jsonb @> CAST(? AS jsonb)
                        ORDER BY recorded_at, id
                        """)
                .params(tenantId.value(), transaction.provider(), reference)
                .query(this::map)
                .list();
    }

    private SettlementView map(ResultSet rs, int row) throws SQLException {
        return new SettlementView(
                new SettlementId(rs.getObject("id", UUID.class)),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                rs.getString("provider"),
                rs.getString("provider_settlement_reference"),
                rs.getString("currency"),
                rs.getBigDecimal("gross_amount"),
                rs.getBigDecimal("fee_amount"),
                rs.getBigDecimal("net_amount"),
                rs.getTimestamp("settled_at").toInstant(),
                rs.getTimestamp("recorded_at").toInstant(),
                Arrays.asList(json.readValue(rs.getString("lines"), SettlementLine[].class)));
    }
}
