package dev.iamkavindu.reconflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.iamkavindu.reconflow.audit.AuditQueries;
import dev.iamkavindu.reconflow.audit.AuditResourceType;
import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.CaseNote;
import dev.iamkavindu.reconflow.cases.events.CaseNoteAdded;
import dev.iamkavindu.reconflow.tenancy.Actor;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.tenancy.UserRole;
import dev.iamkavindu.reconflow.transactions.RecordTransactionCommand;
import dev.iamkavindu.reconflow.transactions.TransactionCommands;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import dev.iamkavindu.reconflow.transactions.events.TransactionRecorded;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Import({TestcontainersConfiguration.class, AuditHistoryIntegrationTest.ActorConfiguration.class})
@SpringBootTest(properties = {"reconflow.kafka.enabled=false", "reconflow.security.enabled=false"})
class AuditHistoryIntegrationTest {

    private static final TenantId TENANT = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Autowired
    AuditQueries queries;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionCommands transactions;

    @Test
    void retainsActorAndTimeOnceAcrossRedeliveryAndIsolatesTenants() {
        var caseId = new CaseId(UUID.randomUUID());
        Instant occurredAt = Instant.parse("2026-10-18T10:00:00Z");
        var note = new CaseNote(UUID.randomUUID(), "Evidence reviewed", "analyst-1", occurredAt);
        var event = new CaseNoteAdded(UUID.randomUUID(), TENANT, caseId, note);
        publish(event);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(queries.forResource(TENANT, AuditResourceType.CASE, caseId.value(), Long.MAX_VALUE, 50))
                        .hasSize(1));
        publish(event);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                events.publishEvent(new CaseNoteAdded(UUID.randomUUID(), TENANT, caseId, note)));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(queries.forResource(TENANT, AuditResourceType.CASE, caseId.value(), Long.MAX_VALUE, 50))
                        .hasSize(2));

        var page = queries.forResource(TENANT, AuditResourceType.CASE, caseId.value(), Long.MAX_VALUE, 1);
        assertThat(page).singleElement().satisfies(entry -> {
            assertThat(entry.actor()).isEqualTo("analyst-1");
            assertThat(entry.action()).isEqualTo("CASE_NOTE_ADDED");
            assertThat(entry.occurredAt()).isEqualTo(occurredAt);
            assertThat(entry.recordedAt()).isNotNull();
        });
        var older = queries.forResource(TENANT, AuditResourceType.CASE, caseId.value(), page.getFirst().sequence(), 1);
        assertThat(older).singleElement().satisfies(entry -> assertThat(entry.eventId()).isEqualTo(event.eventId()));
        assertThat(queries.forResource(new TenantId(UUID.randomUUID()), AuditResourceType.CASE,
                caseId.value(), Long.MAX_VALUE, 50)).isEmpty();
        assertThatThrownBy(() -> queries.forResource(TENANT, AuditResourceType.CASE, caseId.value(), Long.MAX_VALUE, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jdbc.sql("UPDATE audit.audit_entries SET actor = 'rewritten' WHERE event_id = ?")
                .param(event.eventId()).update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit.audit_entries WHERE event_id = ?")
                .param(event.eventId()).update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("TRUNCATE audit.audit_entries").update())
                .isInstanceOf(DataAccessException.class);
        assertThat(queries.forResource(TENANT, AuditResourceType.CASE, caseId.value(), Long.MAX_VALUE, 50)).hasSize(2);
    }

    @Test
    void recordsFinancialEventIdentityActorAndCorrelation() throws Exception {
        var recorded = transactions.recordTransaction(new RecordTransactionCommand(
                "AUDIT-" + UUID.randomUUID(), TransactionType.PAYMENT, new BigDecimal("25.00"),
                Currency.getInstance("USD"), Instant.now()));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(queries.forResource(TENANT, AuditResourceType.TRANSACTION,
                        recorded.transactionId().value(), Long.MAX_VALUE, 50))
                        .singleElement().satisfies(entry -> {
                            assertThat(entry.actor()).isEqualTo("analyst-2");
                            assertThat(entry.correlationId()).isNotNull();
                        }));
        UUID correlationId = UUID.randomUUID();
        var transactionId = new TransactionId(UUID.randomUUID());
        var event = new TransactionRecorded(UUID.randomUUID(), TENANT, transactionId, "audit-reference",
                Instant.parse("2026-10-18T10:00:00Z"), correlationId, "analyst-2");
        var mapper = JsonMapper.builder().findAndAddModules().build();
        String legacy = mapper.writeValueAsString(event).replace(",\"recordedBy\":\"analyst-2\"", "");
        assertThat(mapper.readValue(legacy, TransactionRecorded.class).recordedBy()).isNull();
    }

    private void publish(Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> events.publishEvent(event));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ActorConfiguration {

        @Bean
        @Primary
        CurrentActor testActor() {
            return () -> new Actor(TENANT, "analyst-2", UserRole.ANALYST);
        }
    }
}
