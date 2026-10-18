package dev.iamkavindu.reconflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.iamkavindu.reconflow.audit.AuditQueries;
import dev.iamkavindu.reconflow.audit.AuditResourceType;
import dev.iamkavindu.reconflow.cases.CaseCommands;
import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.CaseQueries;
import dev.iamkavindu.reconflow.cases.InvestigationCommands;
import dev.iamkavindu.reconflow.cases.InvestigationEvidence;
import dev.iamkavindu.reconflow.cases.InvestigationJudgment;
import dev.iamkavindu.reconflow.cases.InvestigationProcessing;
import dev.iamkavindu.reconflow.cases.InvestigationQueries;
import dev.iamkavindu.reconflow.cases.InvestigationResult;
import dev.iamkavindu.reconflow.cases.InvestigationStatus;
import dev.iamkavindu.reconflow.cases.ResolutionType;
import dev.iamkavindu.reconflow.tenancy.Actor;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import dev.iamkavindu.reconflow.tenancy.OperationNotAuthorizedException;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.tenancy.UserRole;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import({TestcontainersConfiguration.class, InvestigationLifecycleIntegrationTest.ActorConfiguration.class})
@SpringBootTest(properties = {"reconflow.kafka.enabled=false", "reconflow.security.enabled=false"})
class InvestigationLifecycleIntegrationTest {

    private static final TenantId TENANT = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Autowired
    InvestigationCommands commands;

    @Autowired
    InvestigationQueries queries;

    @Autowired
    InvestigationProcessing processing;

    @Autowired
    CaseQueries cases;

    @Autowired
    CaseCommands caseCommands;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestActor actor;

    @Autowired
    AuditQueries audit;

    @BeforeEach
    void analyst() {
        actor.current.set(new Actor(TENANT, "analyst", UserRole.ANALYST));
    }

    @Test
    void requestsOnceConcurrentlyRetainsRecommendationAndNeverChangesTheCase() throws Exception {
        CaseId caseId = openCase();
        var originalCase = cases.getCase(TENANT, caseId);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> commands.request(caseId));
            var second = executor.submit(() -> commands.request(caseId));
            var requested = first.get();
            assertThat(second.get()).isEqualTo(requested);
            assertThat(requested.status()).isEqualTo(InvestigationStatus.REQUESTED);
            assertThat(requested.requestedBy()).isEqualTo("analyst");
            var id = requested.investigationId();
            assertThat(processing.start(TENANT, id).status()).isEqualTo(InvestigationStatus.RUNNING);
            var completed = processing.complete(TENANT, id, result(), "contract-test-model", "reconflow-v1");
            assertThat(completed.status()).isEqualTo(InvestigationStatus.COMPLETED);
            assertThat(completed.result()).isEqualTo(result());
            assertThat(processing.complete(TENANT, id, result(), "other-model", "other-prompt")).isEqualTo(completed);
            assertThat(processing.fail(TENANT, id, "Late worker failure")).isEqualTo(completed);
            assertThat(processing.start(TENANT, id)).isEqualTo(completed);
            assertThat(queries.forCase(TENANT, caseId)).containsExactly(completed);
            assertThat(cases.getCase(TENANT, caseId)).isEqualTo(originalCase);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(audit.forResource(TENANT, AuditResourceType.INVESTIGATION, id.value(), Long.MAX_VALUE, 50))
                            .hasSize(3));
        }
    }

    @Test
    void enforcesMembershipTenantAndResolvedCaseBoundariesAndKeepsFailedAttempts() {
        CaseId caseId = openCase();
        actor.current.set(new Actor(TENANT, "viewer", UserRole.VIEWER));
        assertThatThrownBy(() -> commands.request(caseId)).isInstanceOf(OperationNotAuthorizedException.class);
        actor.current.set(new Actor(new TenantId(UUID.randomUUID()), "other", UserRole.ADMIN));
        assertThatThrownBy(() -> commands.request(caseId)).isInstanceOf(NoSuchElementException.class);
        actor.current.set(new Actor(TENANT, "admin", UserRole.ADMIN));
        var requested = commands.request(caseId);
        assertThatThrownBy(() -> queries.get(new TenantId(UUID.randomUUID()), requested.investigationId()))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> processing.start(new TenantId(UUID.randomUUID()), requested.investigationId()))
                .isInstanceOf(NoSuchElementException.class);
        var failed = processing.fail(TENANT, requested.investigationId(), "The evidence service was unavailable.");
        assertThat(failed.status()).isEqualTo(InvestigationStatus.FAILED);
        assertThat(failed.completedAt()).isNotNull();
        var retried = commands.request(caseId);
        assertThat(retried.investigationId()).isNotEqualTo(failed.investigationId());
        assertThat(queries.forCase(TENANT, caseId)).hasSize(2);
        caseCommands.resolveCase(caseId, ResolutionType.MANUAL_REVIEW_COMPLETED, "Human review completed", List.of());
        assertThatThrownBy(() -> commands.request(caseId)).isInstanceOf(IllegalStateException.class);
    }

    private CaseId openCase() {
        var id = new CaseId(UUID.randomUUID());
        jdbc.sql("""
                        INSERT INTO cases.cases
                        (id, tenant_id, reconciliation_id, status, opened_at, updated_at, notes, version)
                        VALUES (?, ?, ?, 'OPEN', now(), now(), '[]', 0)
                        """)
                .params(id.value(), TENANT.value(), UUID.randomUUID()).update();
        return id;
    }

    private InvestigationResult result() {
        return new InvestigationResult("The reported fee explains the gross/net difference.", "Provider-reported fee",
                List.of(new InvestigationEvidence("settlement:demo", "Reported gross 100, fee 3.20, net 96.80")),
                ResolutionType.ACCEPTED_FEE_VARIANCE, 0.8, List.of("The contractual fee policy still needs human review."),
                new InvestigationJudgment(true, 0.95, 0.95, 0));
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
