package dev.iamkavindu.reconflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.iamkavindu.reconflow.cases.CaseCommands;
import dev.iamkavindu.reconflow.cases.CaseListing;
import dev.iamkavindu.reconflow.cases.CaseStatus;
import dev.iamkavindu.reconflow.cases.ResolutionType;
import dev.iamkavindu.reconflow.provider.IngestProviderEventCommand;
import dev.iamkavindu.reconflow.provider.ProviderTransactionCommands;
import dev.iamkavindu.reconflow.provider.ProviderTransactionListing;
import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationListing;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.reporting.Dashboard;
import dev.iamkavindu.reconflow.reporting.DashboardQueries;
import dev.iamkavindu.reconflow.tenancy.Actor;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import dev.iamkavindu.reconflow.tenancy.CurrentTenant;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.tenancy.UserRole;
import dev.iamkavindu.reconflow.transactions.RecordTransactionCommand;
import dev.iamkavindu.reconflow.transactions.TransactionCommands;
import dev.iamkavindu.reconflow.transactions.TransactionListing;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import dev.iamkavindu.reconflow.transactions.TransactionView;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import({TestcontainersConfiguration.class, OperationalSearchIntegrationTest.ActorConfiguration.class})
@SpringBootTest(properties = {"reconflow.kafka.enabled=false", "reconflow.security.enabled=false", "reconflow.reconciliation.scheduling-enabled=false"})
class OperationalSearchIntegrationTest {

    @Autowired
    TransactionCommands commands;

    @Autowired
    TransactionListing transactions;

    @Autowired
    ProviderTransactionCommands providerCommands;

    @Autowired
    ProviderTransactionListing providers;

    @Autowired
    ReconciliationListing reconciliations;

    @Autowired
    CaseListing cases;

    @Autowired
    CaseCommands caseCommands;

    @Autowired
    DashboardQueries dashboard;

    @Autowired
    TestActor actor;

    @Test
    void searchesIdsReferencesStatusesDatesAndPagesWithoutCrossingTenants() {
        var tenant = new TenantId(UUID.randomUUID());
        var other = new TenantId(UUID.randomUUID());
        actor.tenant = tenant;
        Instant first = Instant.parse("2026-10-19T10:00:00Z");
        var matched = transaction("MATCH-" + UUID.randomUUID(), first);
        var review = transaction("REVIEW-" + UUID.randomUUID(), first.plusSeconds(60));
        var pending = transaction("PENDING-" + UUID.randomUUID(), first.plusSeconds(120));
        var matchedProvider = providerCommands.ingestProviderEvent(event(tenant, matched, "100.00"));
        var reviewProvider = providerCommands.ingestProviderEvent(event(tenant, review, "96.80"));
        actor.tenant = other;
        transaction(matched.businessReference(), first);
        actor.tenant = tenant;
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(dashboard.get(tenant)).isEqualTo(new Dashboard(3, 1, 1, 0, 1, 0));
        });
        var all = transactions.search(tenant, new TransactionListing.Filter(null, null, null, null, 50, 0));
        assertThat(all).extracting(TransactionView::transactionId)
                .containsExactly(pending.transactionId(), review.transactionId(), matched.transactionId());
        assertThat(transactions.search(tenant, new TransactionListing.Filter(null, null, null, null, 1, 1)))
                .containsExactly(review);
        assertThat(transactions.search(tenant, new TransactionListing.Filter(null, matched.businessReference(), first, first.plusSeconds(60), 50, 0)))
                .containsExactly(matched);
        assertThat(transactions.search(other, new TransactionListing.Filter(matched.transactionId().value(), null, null, null, 50, 0)))
                .isEmpty();
        assertThat(transactions.search(tenant, new TransactionListing.Filter(null, "' OR true --", null, null, 50, 0)))
                .isEmpty();
        assertThat(providers.search(tenant, new ProviderTransactionListing.Filter(null, reviewProvider.providerTransactionReference(), null,
                first, first.plusSeconds(120), 50, 0))).containsExactly(reviewProvider);
        assertThat(providers.search(other, new ProviderTransactionListing.Filter(reviewProvider.providerTransactionId().value(), null, null,
                null, null, 50, 0))).isEmpty();
        var reviewRows = reconciliations.search(tenant, new ReconciliationListing.Filter(null, review.transactionId().value(),
                reviewProvider.providerTransactionId().value(), review.businessReference(), ReconciliationStatus.REVIEW_REQUIRED, null, null, 50, 0));
        assertThat(reviewRows).hasSize(1);
        assertThat(reconciliations.search(tenant, new ReconciliationListing.Filter(null, null, matchedProvider.providerTransactionId().value(),
                null, ReconciliationStatus.MATCHED, null, null, 50, 0))).hasSize(1);
        assertThat(reconciliations.search(tenant, new ReconciliationListing.Filter(null, pending.transactionId().value(), null,
                null, ReconciliationStatus.PENDING, null, null, 50, 0))).hasSize(1);
        var caseRows = cases.search(tenant, new CaseListing.Filter(null, reviewRows.getFirst().reconciliationId().value(),
                CaseStatus.OPEN, null, null, 50, 0));
        assertThat(caseRows).hasSize(1);
        var caseId = caseRows.getFirst().caseId();
        assertThat(cases.search(other, new CaseListing.Filter(caseId.value(), null, null, null, null, 50, 0))).isEmpty();
        caseCommands.resolveCase(caseId, ResolutionType.MANUAL_REVIEW_COMPLETED, "Reviewed the source evidence", List.of());
        assertThat(cases.search(tenant, new CaseListing.Filter(caseId.value(), null, CaseStatus.OPEN, null, null, 50, 0))).isEmpty();
        assertThat(cases.search(tenant, new CaseListing.Filter(caseId.value(), null, CaseStatus.RESOLVED, null, null, 50, 0))).hasSize(1);
        assertThat(dashboard.get(tenant)).isEqualTo(new Dashboard(3, 1, 1, 0, 0, 1));
        assertThat(dashboard.get(new TenantId(UUID.randomUUID()))).isEqualTo(new Dashboard(0, 0, 0, 0, 0, 0));
    }

    @Test
    void rejectsUnboundedAndReversedQueries() {
        assertThatThrownBy(() -> new TransactionListing.Filter(null, null, null, null, 101, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderTransactionListing.Filter(null, null, null, null, null, 1, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CaseListing.Filter(null, null, null, Instant.MAX, Instant.MIN, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReconciliationListing.Filter(null, null, null, null, null, null, null, 1, 10001)).isInstanceOf(IllegalArgumentException.class);
    }

    private TransactionView transaction(String reference, Instant occurredAt) {
        return commands.recordTransaction(new RecordTransactionCommand(reference, TransactionType.PAYMENT,
                new BigDecimal("100.00"), Currency.getInstance("USD"), occurredAt));
    }

    private IngestProviderEventCommand event(TenantId tenant, TransactionView transaction, String amount) {
        return new IngestProviderEventCommand(tenant, "evt-" + UUID.randomUUID(), "stripe", "pi-" + transaction.businessReference(),
                transaction.businessReference(), ProviderTransactionType.PAYMENT, new BigDecimal(amount), Currency.getInstance("USD"),
                ProviderTransactionStatus.SUCCEEDED, transaction.occurredAt(), UUID.randomUUID(), UUID.randomUUID());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ActorConfiguration {
        @Bean
        @Primary
        TestActor testActor() {
            return new TestActor();
        }
    }

    static class TestActor implements CurrentActor, CurrentTenant {
        volatile TenantId tenant = new TenantId(UUID.randomUUID());

        @Override
        public Actor actor() {
            return new Actor(tenant, "analyst", UserRole.ANALYST);
        }

        @Override
        public TenantId tenantId() {
            return tenant;
        }
    }
}
