package dev.iamkavindu.reconflow.reconciliation.internal.persistence;

import dev.iamkavindu.reconflow.reconciliation.internal.application.CandidateLock;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PostgresCandidateLock implements CandidateLock {

    private final JdbcTemplate jdbcTemplate;

    PostgresCandidateLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(TenantId tenantId, String businessReference) {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                row -> {},
                tenantId.value() + ":" + businessReference);
    }
}
