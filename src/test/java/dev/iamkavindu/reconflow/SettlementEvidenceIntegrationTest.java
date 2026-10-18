package dev.iamkavindu.reconflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.iamkavindu.reconflow.audit.AuditQueries;
import dev.iamkavindu.reconflow.audit.AuditResourceType;
import dev.iamkavindu.reconflow.cases.CaseQueries;
import dev.iamkavindu.reconflow.cases.CaseStatus;
import dev.iamkavindu.reconflow.provider.ImportSettlementCommand;
import dev.iamkavindu.reconflow.provider.IngestProviderEventCommand;
import dev.iamkavindu.reconflow.provider.ProviderTransactionCommands;
import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import dev.iamkavindu.reconflow.provider.ProviderTransactionView;
import dev.iamkavindu.reconflow.provider.SettlementCommands;
import dev.iamkavindu.reconflow.provider.SettlementLine;
import dev.iamkavindu.reconflow.provider.SettlementQueries;
import dev.iamkavindu.reconflow.reconciliation.Discrepancy;
import dev.iamkavindu.reconflow.reconciliation.DiscrepancyType;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationEvidenceQueries;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationQueries;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.RecordTransactionCommand;
import dev.iamkavindu.reconflow.transactions.TransactionCommands;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"reconflow.kafka.enabled=false", "reconflow.security.enabled=false"})
class SettlementEvidenceIntegrationTest {

    private static final TenantId TENANT = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final TenantId OTHER = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    private static final Instant TIME = Instant.parse("2026-10-18T10:00:00Z");

    @Autowired
    SettlementCommands settlements;

    @Autowired
    SettlementQueries settlementQueries;

    @Autowired
    ProviderTransactionCommands provider;

    @Autowired
    TransactionCommands transactions;

    @Autowired
    ReconciliationQueries reconciliations;

    @Autowired
    ReconciliationEvidenceQueries evidence;

    @Autowired
    CaseQueries cases;

    @Autowired
    AuditQueries audit;

    @Test
    void retainsOneImmutableSettlementAndAuditEntryAcrossConcurrentDuplicateImports() throws Exception {
        String reference = "SET-" + UUID.randomUUID();
        String providerReference = "pi-" + UUID.randomUUID();
        var command = command(TENANT, reference, providerReference, "100.00", "3.20", "96.80");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> settlements.importSettlement(command));
            var second = executor.submit(() -> settlements.importSettlement(command));
            var saved = first.get();
            assertThat(second.get()).isEqualTo(saved);
            assertThat(saved.netAmount()).isEqualByComparingTo("96.80");
            assertThatThrownBy(() -> settlements.importSettlement(
                    command(TENANT, reference, providerReference, "101.00", "4.20", "96.80")))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(settlementQueries.getSettlement(TENANT, saved.settlementId())).isEqualTo(saved);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(audit.forResource(TENANT, AuditResourceType.SETTLEMENT,
                            saved.settlementId().value(), Long.MAX_VALUE, 50)).hasSize(1));
        }
    }

    @Test
    void reportedFeeAddsEvidenceButStillRequiresHumanReview() {
        String reference = "ORD-" + UUID.randomUUID();
        String providerReference = "pi-" + reference;
        var settlement = settlements.importSettlement(
                command(TENANT, "SET-" + reference, providerReference, "100.00", "3.20", "96.80"));
        var internal = transactions.recordTransaction(new RecordTransactionCommand(
                reference, TransactionType.PAYMENT, new BigDecimal("100.00"), Currency.getInstance("USD"), TIME));
        var external = ingest(TENANT, reference, providerReference);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var reconciliation = reconciliations.getReconciliation(TENANT, internal.transactionId());
            assertThat(reconciliation.status()).isEqualTo(ReconciliationStatus.REVIEW_REQUIRED);
            assertThat(reconciliation.discrepancies()).extracting(Discrepancy::type)
                    .contains(DiscrepancyType.AMOUNT_MISMATCH, DiscrepancyType.FEE_VARIANCE);
            assertThat(evidence.settlements(TENANT, reconciliation.reconciliationId())).containsExactly(settlement);
            assertThat(cases.getCaseForReconciliation(TENANT, reconciliation.reconciliationId()).status())
                    .isEqualTo(CaseStatus.OPEN);
        });
        assertThat(settlementQueries.forProviderTransaction(TENANT, external.providerTransactionId()))
                .containsExactly(settlement);
    }

    @Test
    void lateSettlementAddsContextWithoutRewritingDeterminationAndIsolatesTenantLinks() {
        String reference = "ORD-" + UUID.randomUUID();
        String providerReference = "pi-" + reference;
        var internal = transactions.recordTransaction(new RecordTransactionCommand(
                reference, TransactionType.PAYMENT, new BigDecimal("100.00"), Currency.getInstance("USD"), TIME));
        var external = ingest(TENANT, reference, providerReference);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(reconciliations.getReconciliation(TENANT, internal.transactionId()).status())
                        .isEqualTo(ReconciliationStatus.REVIEW_REQUIRED));
        var original = reconciliations.getReconciliation(TENANT, internal.transactionId());
        assertThat(original.discrepancies()).extracting(Discrepancy::type).doesNotContain(DiscrepancyType.FEE_VARIANCE);
        var settlement = settlements.importSettlement(
                command(TENANT, "SET-" + reference, providerReference, "100.00", "3.20", "96.80"));
        var otherSettlement = settlements.importSettlement(
                command(OTHER, "SET-" + reference, providerReference, "100.00", "3.20", "96.80"));
        var otherProvider = ingest(OTHER, reference, providerReference);
        assertThat(reconciliations.getReconciliation(TENANT, internal.transactionId())).isEqualTo(original);
        assertThat(evidence.settlements(TENANT, original.reconciliationId())).containsExactly(settlement);
        assertThat(settlementQueries.forProviderTransaction(OTHER, otherProvider.providerTransactionId()))
                .containsExactly(otherSettlement);
        assertThatThrownBy(() -> settlementQueries.getSettlement(OTHER, settlement.settlementId()))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> settlementQueries.forProviderTransaction(OTHER, external.providerTransactionId()))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> evidence.settlements(OTHER, original.reconciliationId()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void rejectsUnbalancedDuplicateAndOverprecisionLines() {
        String reference = UUID.randomUUID().toString();
        assertThatThrownBy(() -> new SettlementLine(reference,
                new BigDecimal("100.00"), new BigDecimal("3.20"), new BigDecimal("97.00")))
                .isInstanceOf(IllegalArgumentException.class);
        var source = command(TENANT, reference, reference, "100.00", "3.20", "96.80");
        assertThatThrownBy(() -> settlements.importSettlement(new ImportSettlementCommand(
                TENANT, "stripe", reference, Currency.getInstance("USD"), source.grossAmount(), source.feeAmount(),
                source.netAmount(), TIME, List.of(source.lines().getFirst(), source.lines().getFirst()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settlements.importSettlement(
                command(TENANT, reference, reference, "100.001", "3.201", "96.800")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ImportSettlementCommand command(TenantId tenant, String reference, String providerReference,
            String gross, String fee, String net) {
        var line = new SettlementLine(providerReference, new BigDecimal(gross), new BigDecimal(fee), new BigDecimal(net));
        return new ImportSettlementCommand(tenant, "stripe", reference, Currency.getInstance("USD"),
                line.grossAmount(), line.feeAmount(), line.netAmount(), TIME, List.of(line));
    }

    private ProviderTransactionView ingest(TenantId tenant, String reference, String providerReference) {
        return provider.ingestProviderEvent(new IngestProviderEventCommand(tenant, "evt-" + reference, "stripe",
                providerReference, reference, ProviderTransactionType.PAYMENT, new BigDecimal("96.80"),
                Currency.getInstance("USD"), ProviderTransactionStatus.SUCCEEDED, TIME, UUID.randomUUID(), UUID.randomUUID()));
    }
}
