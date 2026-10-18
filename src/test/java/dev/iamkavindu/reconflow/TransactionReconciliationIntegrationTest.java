package dev.iamkavindu.reconflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.iamkavindu.reconflow.reconciliation.ReconciliationQueries;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationView;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.RecordTransactionCommand;
import dev.iamkavindu.reconflow.transactions.TransactionCommands;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import dev.iamkavindu.reconflow.transactions.TransactionView;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TransactionReconciliationIntegrationTest {

    private static final TenantId CONFIGURED_TENANT =
            new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Autowired
    private TransactionCommands transactionCommands;

    @Autowired
    private ReconciliationQueries reconciliationQueries;

    @Test
    void recordedTransactionStartsPendingReconciliationInSeparateModuleTransaction() throws InterruptedException {
        TransactionView transaction = transactionCommands.recordTransaction(new RecordTransactionCommand(
                "ORD-2001",
                TransactionType.PAYMENT,
                new BigDecimal("42.50"),
                Currency.getInstance("USD"),
                Instant.parse("2026-10-10T09:25:00Z")));

        ReconciliationView reconciliation = awaitReconciliation(transaction);

        assertThat(reconciliation.tenantId()).isEqualTo(CONFIGURED_TENANT);
        assertThat(reconciliation.transactionId()).isEqualTo(transaction.transactionId());
        assertThat(reconciliation.status()).isEqualTo(ReconciliationStatus.PENDING);
        assertThat(reconciliation.startedAt()).isEqualTo(transaction.recordedAt());
        assertThat(reconciliation.expiresAt()).isEqualTo(transaction.recordedAt().plus(Duration.ofMinutes(10)));
    }

    private ReconciliationView awaitReconciliation(TransactionView transaction) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(5);

        while (Instant.now().isBefore(deadline)) {
            try {
                return reconciliationQueries.getReconciliation(CONFIGURED_TENANT, transaction.transactionId());
            } catch (NoSuchElementException ignored) {
                Thread.sleep(25);
            }
        }

        throw new AssertionError("Reconciliation was not created within five seconds");
    }
}
