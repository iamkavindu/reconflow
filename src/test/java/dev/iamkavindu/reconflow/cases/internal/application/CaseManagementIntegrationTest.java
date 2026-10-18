package dev.iamkavindu.reconflow.cases.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.iamkavindu.reconflow.TestcontainersConfiguration;
import dev.iamkavindu.reconflow.cases.CaseCommands;
import dev.iamkavindu.reconflow.cases.CaseQueries;
import dev.iamkavindu.reconflow.cases.CaseStatus;
import dev.iamkavindu.reconflow.cases.CaseView;
import dev.iamkavindu.reconflow.cases.ResolutionType;
import dev.iamkavindu.reconflow.cases.internal.domain.CaseRepository;
import dev.iamkavindu.reconflow.provider.IngestProviderEventCommand;
import dev.iamkavindu.reconflow.provider.ProviderTransactionCommands;
import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationQueries;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationView;
import dev.iamkavindu.reconflow.tenancy.Actor;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import dev.iamkavindu.reconflow.tenancy.OperationNotAuthorizedException;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.tenancy.UserRole;
import dev.iamkavindu.reconflow.transactions.RecordTransactionCommand;
import dev.iamkavindu.reconflow.transactions.TransactionCommands;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import({TestcontainersConfiguration.class, CaseManagementIntegrationTest.ActorConfiguration.class})
@SpringBootTest(properties = "reconflow.kafka.enabled=false")
class CaseManagementIntegrationTest {

    private static final TenantId TENANT = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Autowired
    private TransactionCommands transactions;

    @Autowired
    private ProviderTransactionCommands provider;

    @Autowired
    private ReconciliationQueries reconciliations;

    @Autowired
    private CaseQueries cases;

    @Autowired
    private CaseCommands commands;

    @Autowired
    private CaseOpeningService opening;

    @Autowired
    private TestActor actor;

    @Autowired
    private CaseRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void useAnalyst() {
        actor.current.set(new Actor(TENANT, "analyst-1", UserRole.ANALYST));
    }

    @Test
    void opensOneCasePreservesNotesAndResolvesWithoutRewritingReconciliation() throws Exception {
        var reconciliation = exception();
        var opened = awaitCase(reconciliation);

        var review = commands.startReview(opened.caseId());
        var noted = commands.addNote(opened.caseId(), "Provider fee matches the documented schedule");
        var resolved = commands.resolveCase(
                opened.caseId(),
                ResolutionType.ACCEPTED_FEE_VARIANCE,
                "Reviewed supporting evidence",
                List.of("fee-policy"));
        opening.open(
                TENANT,
                reconciliation.reconciliationId(),
                reconciliation.decidedAt(),
                UUID.randomUUID(),
                UUID.randomUUID());

        assertThat(review.status()).isEqualTo(CaseStatus.UNDER_REVIEW);
        assertThat(noted.notes()).hasSize(1);
        assertThat(resolved.status()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(resolved.resolution().resolvedBy()).isEqualTo("analyst-1");
        assertThat(cases.getCase(TENANT, opened.caseId())).isEqualTo(resolved);
        assertThat(cases.getCaseForReconciliation(TENANT, reconciliation.reconciliationId()))
                .isEqualTo(resolved);
        assertThat(reconciliations.getReconciliationById(TENANT, reconciliation.reconciliationId()))
                .isEqualTo(reconciliation);
        assertThatThrownBy(() -> commands.resolveCase(
                        opened.caseId(), ResolutionType.MANUAL_REVIEW_COMPLETED, "Another resolution", List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsViewersAndCrossTenantMutations() throws Exception {
        var opened = awaitCase(exception());
        actor.current.set(new Actor(TENANT, "viewer-1", UserRole.VIEWER));

        assertThatThrownBy(() -> commands.startReview(opened.caseId()))
                .isInstanceOf(OperationNotAuthorizedException.class);
        assertThatThrownBy(() -> commands.addNote(opened.caseId(), "Not permitted"))
                .isInstanceOf(OperationNotAuthorizedException.class);
        assertThatThrownBy(() -> commands.resolveCase(
                        opened.caseId(), ResolutionType.MATCH_CONFIRMED, "Not permitted", List.of()))
                .isInstanceOf(OperationNotAuthorizedException.class);
        actor.current.set(new Actor(new TenantId(UUID.randomUUID()), "other-analyst", UserRole.ANALYST));
        assertThatThrownBy(() -> commands.resolveCase(
                        opened.caseId(), ResolutionType.MATCH_CONFIRMED, "Wrong tenant", List.of()))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(cases.getCase(TENANT, opened.caseId()).status()).isEqualTo(CaseStatus.OPEN);
    }

    @Test
    void administratorCanResolveAnOpenCaseWithoutStartingReview() throws Exception {
        var opened = awaitCase(exception());
        actor.current.set(new Actor(TENANT, "admin-1", UserRole.ADMIN));

        var resolved = commands.resolveCase(
                opened.caseId(),
                ResolutionType.MANUAL_REVIEW_COMPLETED,
                "Evidence reviewed by administrator",
                List.of());

        assertThat(resolved.status()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(resolved.resolution().resolvedBy()).isEqualTo("admin-1");
    }

    @Test
    void optimisticLockingPreventsStaleCaseUpdatesFromLosingNotes() throws Exception {
        var opened = awaitCase(exception());
        var template = new TransactionTemplate(transactionManager);
        var stale = template.execute(status -> repository.findByTenantIdAndId(TENANT, opened.caseId()).orElseThrow());
        commands.addNote(opened.caseId(), "First analyst's evidence");
        var staleUpdate = stale.startReview(Instant.now());

        assertThatThrownBy(() -> template.executeWithoutResult(status -> repository.save(staleUpdate)))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(cases.getCase(TENANT, opened.caseId()).notes()).hasSize(1);
    }

    private ReconciliationView exception() throws InterruptedException {
        String reference = UUID.randomUUID().toString();
        var transaction = transactions.recordTransaction(new RecordTransactionCommand(
                reference,
                TransactionType.PAYMENT,
                new BigDecimal("100.00"),
                Currency.getInstance("USD"),
                Instant.now()));
        provider.ingestProviderEvent(new IngestProviderEventCommand(
                TENANT,
                UUID.randomUUID().toString(),
                "stripe",
                UUID.randomUUID().toString(),
                reference,
                ProviderTransactionType.PAYMENT,
                new BigDecimal("96.80"),
                Currency.getInstance("USD"),
                ProviderTransactionStatus.SUCCEEDED,
                transaction.occurredAt(),
                UUID.randomUUID(),
                UUID.randomUUID()));
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            try {
                var result = reconciliations.getReconciliation(TENANT, transaction.transactionId());
                if (result.status() == ReconciliationStatus.REVIEW_REQUIRED) {
                    return result;
                }
            } catch (NoSuchElementException ignored) {
                // Await module delivery.
            }
            Thread.sleep(25);
        }
        throw new AssertionError("review-required reconciliation was not created");
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
        throw new AssertionError("exception case was not created");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ActorConfiguration {

        @Bean
        @Primary
        TestActor testActor() {
            return new TestActor();
        }
    }

    static class TestActor implements CurrentActor {

        final AtomicReference<Actor> current = new AtomicReference<>();

        @Override
        public Actor actor() {
            return current.get();
        }
    }
}
