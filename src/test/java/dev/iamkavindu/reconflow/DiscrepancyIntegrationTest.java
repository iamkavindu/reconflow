package dev.iamkavindu.reconflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.iamkavindu.reconflow.provider.IngestProviderEventCommand;
import dev.iamkavindu.reconflow.provider.ProviderTransactionCommands;
import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import dev.iamkavindu.reconflow.reconciliation.DiscrepancyType;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationQueries;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationView;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.RecordTransactionCommand;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "reconflow.kafka.enabled=false")
class DiscrepancyIntegrationTest {

    private static final TenantId TENANT = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final Instant TIME = Instant.parse("2026-10-10T10:00:00Z");

    @Autowired
    private TransactionCommands transactions;

    @Autowired
    private ProviderTransactionCommands provider;

    @Autowired
    private ReconciliationQueries queries;

    @Test
    void persistsAmountAndCurrencyDiscrepanciesWithFailedRuleEvidence() throws Exception {
        String reference = UUID.randomUUID().toString();
        var internal = record(reference, TransactionType.PAYMENT);
        provider.ingestProviderEvent(command(reference, "96.80", "EUR", ProviderTransactionType.PAYMENT));

        var result = awaitReview(internal);

        assertThat(result.discrepancies())
                .extracting(discrepancy -> discrepancy.type())
                .containsExactly(DiscrepancyType.AMOUNT_MISMATCH, DiscrepancyType.CURRENCY_MISMATCH);
        assertThat(result.ruleEvaluations()).hasSize(6);
        assertThat(result.discrepancies().getFirst().expected()).isEqualTo("100.0000");
        assertThat(result.discrepancies().getFirst().actual()).isEqualTo("96.8000");
        assertThat(result.comparedProviderTransactionIds()).hasSize(1);
        assertThat(result.decidedAt()).isNotNull();
        assertThat(queries.getReconciliation(TENANT, internal.transactionId())).isEqualTo(result);
    }

    @Test
    void classifiesRefundAmountDifferencesAsRefundMismatch() throws Exception {
        String reference = UUID.randomUUID().toString();
        var internal = record(reference, TransactionType.REFUND);
        provider.ingestProviderEvent(command(reference, "90.00", "USD", ProviderTransactionType.REFUND));

        assertThat(awaitReview(internal).discrepancies())
                .extracting(discrepancy -> discrepancy.type())
                .containsExactly(DiscrepancyType.REFUND_MISMATCH);
    }

    @Test
    void detectsDistinctProviderRecordsAsDuplicateCandidates() throws Exception {
        String reference = UUID.randomUUID().toString();
        provider.ingestProviderEvent(command(reference, "100.00", "USD", ProviderTransactionType.PAYMENT));
        provider.ingestProviderEvent(command(reference, "100.00", "USD", ProviderTransactionType.PAYMENT));
        var internal = record(reference, TransactionType.PAYMENT);

        var result = awaitReview(internal);

        assertThat(result.discrepancies())
                .extracting(discrepancy -> discrepancy.type())
                .containsExactly(DiscrepancyType.DUPLICATE_TRANSACTION);
        assertThat(result.comparedProviderTransactionIds()).hasSize(2);
    }

    @Test
    void replayingProviderEventPreservesOriginalReviewDecision() throws Exception {
        String reference = UUID.randomUUID().toString();
        var internal = record(reference, TransactionType.PAYMENT);
        var command = command(reference, "96.80", "USD", ProviderTransactionType.PAYMENT);
        provider.ingestProviderEvent(command);
        var first = awaitReview(internal);

        provider.ingestProviderEvent(command);

        assertThat(queries.getReconciliation(TENANT, internal.transactionId())).isEqualTo(first);
    }

    private TransactionView record(String reference, TransactionType type) {
        return transactions.recordTransaction(new RecordTransactionCommand(
                reference, type, new BigDecimal("100.00"), Currency.getInstance("USD"), TIME));
    }

    private static IngestProviderEventCommand command(
            String reference, String amount, String currency, ProviderTransactionType type) {
        return new IngestProviderEventCommand(
                TENANT,
                UUID.randomUUID().toString(),
                "stripe",
                UUID.randomUUID().toString(),
                reference,
                type,
                new BigDecimal(amount),
                Currency.getInstance(currency),
                ProviderTransactionStatus.SUCCEEDED,
                TIME,
                UUID.randomUUID(),
                UUID.randomUUID());
    }

    private ReconciliationView awaitReview(TransactionView transaction) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            try {
                var result = queries.getReconciliation(TENANT, transaction.transactionId());
                if (result.status() == ReconciliationStatus.REVIEW_REQUIRED) {
                    return result;
                }
            } catch (NoSuchElementException ignored) {
                // Reconciliation is created asynchronously after the financial records commit.
            }
            Thread.sleep(25);
        }
        throw new AssertionError("reconciliation did not require review within ten seconds");
    }
}
