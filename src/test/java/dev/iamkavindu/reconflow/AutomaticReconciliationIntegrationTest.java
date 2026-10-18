package dev.iamkavindu.reconflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.iamkavindu.reconflow.provider.IngestProviderEventCommand;
import dev.iamkavindu.reconflow.provider.ProviderTransactionCommands;
import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import dev.iamkavindu.reconflow.provider.ProviderTransactionView;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationQueries;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationView;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.RecordTransactionCommand;
import dev.iamkavindu.reconflow.transactions.TransactionCandidates;
import dev.iamkavindu.reconflow.transactions.TransactionCommands;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import dev.iamkavindu.reconflow.transactions.TransactionView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "reconflow.kafka.enabled=false")
class AutomaticReconciliationIntegrationTest {

    private static final TenantId TENANT = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final Instant TIME = Instant.parse("2026-10-10T10:00:00Z");

    @Autowired
    private TransactionCommands transactions;

    @Autowired
    private ProviderTransactionCommands providerTransactions;

    @Autowired
    private ReconciliationQueries reconciliations;

    @Autowired
    private TransactionCandidates candidates;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void matchesWhenInternalRecordArrivesFirstAndPersistsRuleEvidence() throws Exception {
        String reference = "ORD-" + UUID.randomUUID();
        var transaction = record(reference);
        var provider = ingest(reference);

        assertMatch(awaitMatch(transaction), provider);
    }

    @Test
    void matchesWhenProviderRecordArrivesFirst() throws Exception {
        String reference = "ORD-" + UUID.randomUUID();
        var provider = ingest(reference);
        var transaction = record(reference);

        assertMatch(awaitMatch(transaction), provider);
    }

    @Test
    void replayingProviderDeliveryDoesNotCreateAnotherReconciliation() throws Exception {
        String reference = "ORD-" + UUID.randomUUID();
        var transaction = record(reference);
        var provider = ingest(reference);
        var first = awaitMatch(transaction);
        var duplicate = ingest(reference);

        assertThat(duplicate).isEqualTo(provider);
        assertThat(reconciliations.getReconciliation(TENANT, transaction.transactionId())).isEqualTo(first);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM reconciliation.reconciliations WHERE tenant_id = ? AND transaction_id = ?",
                        Integer.class,
                        TENANT.value(),
                        transaction.transactionId().value()))
                .isEqualTo(1);
    }

    @Test
    void candidateQueriesDoNotExposeAnotherTenantsRecords() {
        String reference = "ORD-" + UUID.randomUUID();
        record(reference);

        assertThat(candidates.findByBusinessReference(new TenantId(UUID.randomUUID()), reference)).isEmpty();
        assertThat(candidates.findByBusinessReference(TENANT, reference)).hasSize(1);
    }

    private TransactionView record(String reference) {
        return transactions.recordTransaction(new RecordTransactionCommand(
                reference, TransactionType.PAYMENT, new BigDecimal("100.00"), Currency.getInstance("USD"), TIME));
    }

    private ProviderTransactionView ingest(String reference) {
        return providerTransactions.ingestProviderEvent(new IngestProviderEventCommand(
                TENANT,
                "evt-" + reference,
                "stripe",
                "pi-" + reference,
                reference,
                ProviderTransactionType.PAYMENT,
                new BigDecimal("100.00"),
                Currency.getInstance("USD"),
                ProviderTransactionStatus.SUCCEEDED,
                TIME,
                UUID.randomUUID(),
                UUID.randomUUID()));
    }

    private ReconciliationView awaitMatch(TransactionView transaction) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            try {
                var result = reconciliations.getReconciliation(TENANT, transaction.transactionId());
                if (result.status() == ReconciliationStatus.MATCHED) {
                    return result;
                }
            } catch (NoSuchElementException ignored) {
                // The module listener commits the reconciliation in its own transaction.
            }
            Thread.sleep(25);
        }
        throw new AssertionError("reconciliation did not become MATCHED within ten seconds");
    }

    private static void assertMatch(ReconciliationView result, ProviderTransactionView provider) {
        assertThat(result.status()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(result.providerTransactionId()).isEqualTo(provider.providerTransactionId());
        assertThat(result.matchedAt()).isNotNull();
        assertThat(result.ruleEvaluations()).hasSize(6).allMatch(rule -> rule.satisfied());
    }
}
