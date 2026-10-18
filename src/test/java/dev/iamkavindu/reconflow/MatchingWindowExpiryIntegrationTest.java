package dev.iamkavindu.reconflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.iamkavindu.reconflow.cases.CaseQueries;
import dev.iamkavindu.reconflow.cases.CaseStatus;
import dev.iamkavindu.reconflow.cases.CaseView;
import dev.iamkavindu.reconflow.provider.IngestProviderEventCommand;
import dev.iamkavindu.reconflow.provider.ProviderTransactionCommands;
import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import dev.iamkavindu.reconflow.provider.ProviderTransactionView;
import dev.iamkavindu.reconflow.reconciliation.DiscrepancyType;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationMaintenance;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationQueries;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationView;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.RecordTransactionCommand;
import dev.iamkavindu.reconflow.transactions.TransactionCommands;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import({TestcontainersConfiguration.class, MatchingWindowExpiryIntegrationTest.ClockConfiguration.class})
@SpringBootTest(properties = {"reconflow.kafka.enabled=false", "reconflow.reconciliation.scheduling-enabled=false"})
class MatchingWindowExpiryIntegrationTest {

    private static final Instant START = Instant.parse("2026-10-10T10:00:00Z");
    private static final TenantId TENANT = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Autowired
    private MutableClock clock;

    @Autowired
    private TransactionCommands transactions;

    @Autowired
    private ProviderTransactionCommands provider;

    @Autowired
    private ReconciliationQueries queries;

    @Autowired
    private ReconciliationMaintenance maintenance;

    @Autowired
    private CaseQueries cases;

    @BeforeEach
    void resetClock() {
        clock.current.set(START);
    }

    @Test
    void expiresMissingProviderExactlyAtDeadlineAndRepeatedScansPreserveDecision() throws Exception {
        var transaction = transactions.recordTransaction(new RecordTransactionCommand(
                UUID.randomUUID().toString(),
                TransactionType.PAYMENT,
                new BigDecimal("100.00"),
                Currency.getInstance("USD"),
                START));
        var pending = await(
                () -> queries.getReconciliation(TENANT, transaction.transactionId()), ReconciliationStatus.PENDING);

        clock.current.set(START.plusSeconds(599));
        maintenance.processExpiredMatchingWindows();
        assertThat(queries.getReconciliation(TENANT, transaction.transactionId())
                        .status())
                .isEqualTo(ReconciliationStatus.PENDING);

        clock.current.set(START.plusSeconds(600));
        maintenance.processExpiredMatchingWindows();
        var unmatched = queries.getReconciliation(TENANT, transaction.transactionId());

        assertThat(unmatched.reconciliationId()).isEqualTo(pending.reconciliationId());
        assertThat(unmatched.status()).isEqualTo(ReconciliationStatus.UNMATCHED);
        assertThat(unmatched.discrepancies())
                .extracting(discrepancy -> discrepancy.type())
                .containsExactly(DiscrepancyType.MISSING_PROVIDER_TRANSACTION);
        maintenance.processExpiredMatchingWindows();
        assertThat(queries.getReconciliation(TENANT, transaction.transactionId()))
                .isEqualTo(unmatched);
        assertThat(awaitCase(unmatched).status()).isEqualTo(CaseStatus.OPEN);
    }

    @Test
    void expiresOrphanProviderAndProtectsTenantScopedLookup() throws Exception {
        var payment = provider(UUID.randomUUID().toString());
        await(
                () -> queries.getProviderReconciliation(TENANT, payment.providerTransactionId()),
                ReconciliationStatus.PENDING);

        clock.current.set(START.plusSeconds(600));
        maintenance.processExpiredMatchingWindows();
        var unmatched = queries.getProviderReconciliation(TENANT, payment.providerTransactionId());

        assertThat(unmatched.status()).isEqualTo(ReconciliationStatus.UNMATCHED);
        assertThat(unmatched.transactionId()).isNull();
        assertThat(unmatched.providerOriginId()).isEqualTo(payment.providerTransactionId());
        assertThat(unmatched.discrepancies())
                .extracting(discrepancy -> discrepancy.type())
                .containsExactly(DiscrepancyType.MISSING_INTERNAL_TRANSACTION);
        assertThatThrownBy(() -> queries.getProviderReconciliation(
                        new TenantId(UUID.randomUUID()), payment.providerTransactionId()))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(awaitCase(unmatched).status()).isEqualTo(CaseStatus.OPEN);
    }

    @Test
    void providerFirstMatchUsesExistingReconciliationAndDoesNotExpireIt() throws Exception {
        String reference = UUID.randomUUID().toString();
        var payment = provider(reference);
        var pending = await(
                () -> queries.getProviderReconciliation(TENANT, payment.providerTransactionId()),
                ReconciliationStatus.PENDING);

        clock.current.set(START.plusSeconds(30));
        var transaction = transactions.recordTransaction(new RecordTransactionCommand(
                reference, TransactionType.PAYMENT, new BigDecimal("100.00"), Currency.getInstance("USD"), START));
        var matched = await(
                () -> queries.getReconciliation(TENANT, transaction.transactionId()), ReconciliationStatus.MATCHED);

        assertThat(matched.reconciliationId()).isEqualTo(pending.reconciliationId());
        clock.current.set(START.plusSeconds(601));
        maintenance.processExpiredMatchingWindows();
        assertThat(queries.getProviderReconciliation(TENANT, payment.providerTransactionId()))
                .isEqualTo(matched);
        assertThatThrownBy(() -> cases.getCaseForReconciliation(TENANT, matched.reconciliationId()))
                .isInstanceOf(NoSuchElementException.class);
    }

    private CaseView awaitCase(ReconciliationView reconciliation) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            try {
                return cases.getCaseForReconciliation(TENANT, reconciliation.reconciliationId());
            } catch (NoSuchElementException ignored) {
                Thread.sleep(25);
            }
        }
        throw new AssertionError("exception case was not opened");
    }

    private ProviderTransactionView provider(String reference) {
        return provider.ingestProviderEvent(new IngestProviderEventCommand(
                TENANT,
                UUID.randomUUID().toString(),
                "stripe",
                UUID.randomUUID().toString(),
                reference,
                ProviderTransactionType.PAYMENT,
                new BigDecimal("100.00"),
                Currency.getInstance("USD"),
                ProviderTransactionStatus.SUCCEEDED,
                START,
                UUID.randomUUID(),
                UUID.randomUUID()));
    }

    private static ReconciliationView await(Supplier<ReconciliationView> query, ReconciliationStatus status)
            throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            try {
                var result = query.get();
                if (result.status() == status) {
                    return result;
                }
            } catch (NoSuchElementException ignored) {
                // Wait for asynchronous module delivery to commit.
            }
            Thread.sleep(25);
        }
        throw new AssertionError("expected reconciliation status " + status);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {

        final AtomicReference<Instant> current = new AtomicReference<>(START);

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
