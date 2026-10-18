package dev.iamkavindu.reconflow.reconciliation.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconciliationTest {

    @Test
    void startsPendingForTheRequestedTransactionAndWindow() {
        ReconciliationId reconciliationId = new ReconciliationId(UUID.randomUUID());
        TenantId tenantId = new TenantId(UUID.randomUUID());
        TransactionId transactionId = new TransactionId(UUID.randomUUID());
        MatchingWindow window = new MatchingWindow(
                Instant.parse("2026-10-10T09:25:00Z"), Instant.parse("2026-10-10T09:35:00Z"));

        Reconciliation reconciliation =
                Reconciliation.pending(reconciliationId, tenantId, transactionId, window);

        assertThat(reconciliation.reconciliationId()).isEqualTo(reconciliationId);
        assertThat(reconciliation.tenantId()).isEqualTo(tenantId);
        assertThat(reconciliation.transactionId()).isEqualTo(transactionId);
        assertThat(reconciliation.status()).isEqualTo(ReconciliationStatus.PENDING);
        assertThat(reconciliation.matchingWindow()).isEqualTo(window);
    }
}
