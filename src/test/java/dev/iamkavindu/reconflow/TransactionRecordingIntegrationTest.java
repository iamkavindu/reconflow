package dev.iamkavindu.reconflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.RecordTransactionCommand;
import dev.iamkavindu.reconflow.transactions.TransactionCommands;
import dev.iamkavindu.reconflow.transactions.TransactionQueries;
import dev.iamkavindu.reconflow.transactions.TransactionStatus;
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
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"reconflow.kafka.enabled=false", "reconflow.security.enabled=false"})
@Transactional
class TransactionRecordingIntegrationTest {

    private static final TenantId CONFIGURED_TENANT =
            new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Autowired
    private TransactionCommands commands;

    @Autowired
    private TransactionQueries queries;

    @Test
    void persistsAndReloadsTransactionThroughTenantAwareApplicationBoundary() {
        TransactionView recorded = commands.recordTransaction(new RecordTransactionCommand(
                "ORD-1001",
                TransactionType.PAYMENT,
                new BigDecimal("100.00"),
                Currency.getInstance("USD"),
                Instant.parse("2026-10-10T09:25:00Z")));

        TransactionView reloaded = queries.getTransaction(CONFIGURED_TENANT, recorded.transactionId());

        assertThat(reloaded).isEqualTo(recorded);
        assertThat(reloaded.status()).isEqualTo(TransactionStatus.RECORDED);
        assertThat(reloaded.amount()).isEqualByComparingTo("100.00");
        assertThat(reloaded.currency()).isEqualTo("USD");
    }

    @Test
    void doesNotReturnTransactionForAnotherTenant() {
        TransactionView recorded = commands.recordTransaction(new RecordTransactionCommand(
                "ORD-1002",
                TransactionType.PAYMENT,
                new BigDecimal("25.00"),
                Currency.getInstance("USD"),
                Instant.parse("2026-10-10T10:00:00Z")));

        TenantId anotherTenant = new TenantId(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));

        assertThatThrownBy(() -> queries.getTransaction(anotherTenant, recorded.transactionId()))
                .isInstanceOf(NoSuchElementException.class);
    }
}
