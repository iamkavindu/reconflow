package dev.iamkavindu.reconflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.iamkavindu.reconflow.audit.AuditQueries;
import dev.iamkavindu.reconflow.audit.AuditResourceType;
import dev.iamkavindu.reconflow.provider.ProviderTransactionListing;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationListing;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.tenancy.Actor;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import dev.iamkavindu.reconflow.tenancy.CurrentTenant;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.tenancy.UserRole;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;

@Import({TestcontainersConfiguration.class, ProviderSimulationIntegrationTest.ActorConfiguration.class})
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "reconflow.kafka.enabled=true",
            "reconflow.security.enabled=false",
            "reconflow.reconciliation.scheduling-enabled=false"
        })
class ProviderSimulationIntegrationTest {
    private static final TenantId TENANT = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.0");
    private static String bootstrap;

    @DynamicPropertySource
    static void broker(DynamicPropertyRegistry registry) {
        bootstrap = System.getProperty("reconflow.test.kafka-bootstrap-servers");
        if (bootstrap == null) {
            KAFKA.start();
            bootstrap = KAFKA.getBootstrapServers();
        }
        try (var admin = Admin.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrap,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG,
                2000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG,
                2000))) {
            await().atMost(Duration.ofSeconds(60))
                    .untilAsserted(() ->
                            assertThat(admin.describeCluster().nodes().get()).isNotEmpty());
        }
        registry.add("spring.kafka.bootstrap-servers", () -> bootstrap);
    }

    @AfterAll
    static void stopBroker() {
        if (KAFKA.isRunning()) {
            KAFKA.stop();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    TestActor actor;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ReconciliationListing reconciliations;

    @Autowired
    ProviderTransactionListing providers;

    @Autowired
    AuditQueries audit;

    @BeforeEach
    void analyst() {
        actor.current = new Actor(TENANT, "analyst", UserRole.ANALYST);
    }

    @Test
    void sendsDuplicateDeliveriesThroughKafkaWithOneFinancialAndReconciliationEffect() throws Exception {
        String ref = "SIM-" + UUID.randomUUID();
        String source = source(ref, "100.00");
        mvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {"businessReference":"%s","type":"PAYMENT","amount":100.00,"currency":"USD","occurredAt":"2026-10-19T10:00:00Z"}
                """.formatted(ref)))
                .andExpect(status().isCreated());
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/provider-events/simulate")
                            .header("X-Tenant-Id", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(source))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.tenantId.value").value(TENANT.value().toString()));
        }
        String barrier = "BARRIER-" + UUID.randomUUID();
        mvc.perform(post("/api/provider-events/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(source(barrier, "100.00")))
                .andExpect(status().isAccepted());
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(count(TENANT, barrier)).isEqualTo(1));
        assertThat(count(TENANT, ref)).isEqualTo(1);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(reconciliations.search(
                                TENANT,
                                new ReconciliationListing.Filter(
                                        null, null, null, ref, ReconciliationStatus.MATCHED, null, null, 50, 0)))
                        .hasSize(1));
        var provider = providers
                .search(TENANT, new ProviderTransactionListing.Filter(null, "pi-" + ref, null, null, null, 50, 0))
                .getFirst();
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(audit.forResource(
                                TENANT,
                                AuditResourceType.PROVIDER_TRANSACTION,
                                provider.providerTransactionId().value(),
                                Long.MAX_VALUE,
                                50))
                        .hasSize(1));
    }

    @Test
    void derivesScopeFromTheActorAndRejectsViewersAndInvalidFinancialInput() throws Exception {
        String ref = "TENANT-" + UUID.randomUUID();
        String source = source(ref, "100.00");
        mvc.perform(post("/api/provider-events/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(source))
                .andExpect(status().isAccepted());
        var other = new TenantId(UUID.randomUUID());
        actor.current = new Actor(other, "other-analyst", UserRole.ANALYST);
        mvc.perform(post("/api/provider-events/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(source))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.tenantId.value").value(other.value().toString()));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(count(TENANT, ref)).isEqualTo(1);
            assertThat(count(other, ref)).isEqualTo(1);
        });
        actor.current = new Actor(TENANT, "viewer", UserRole.VIEWER);
        mvc.perform(post("/api/provider-events/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(source))
                .andExpect(status().isForbidden());
        actor.current = new Actor(TENANT, "analyst", UserRole.ANALYST);
        for (String amount : new String[] {"0", "-1", "1.001", "1000000000000000"}) {
            String invalid = "INVALID-" + UUID.randomUUID();
            mvc.perform(post("/api/provider-events/simulate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(source(invalid, amount)))
                    .andExpect(status().isBadRequest());
            assertThat(count(TENANT, invalid)).isZero();
        }
    }

    private long count(TenantId tenant, String reference) {
        return jdbc.sql(
                        "SELECT count(*) FROM provider.provider_transactions WHERE tenant_id = ? AND business_reference = ?")
                .params(tenant.value(), reference)
                .query(Long.class)
                .single();
    }

    private String source(String reference, String amount) {
        return """
                {"providerEventId":"evt-%s","providerTransactionReference":"pi-%s","businessReference":"%s",
                "type":"PAYMENT","amount":%s,"currency":"USD","status":"SUCCEEDED","occurredAt":"2026-10-19T10:00:00Z",
                "tenantId":"99999999-9999-9999-9999-999999999999"}
                """.formatted(reference, reference, reference, amount);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ActorConfiguration {
        @Bean
        @Primary
        TestActor testActor() {
            return new TestActor();
        }

        @Bean
        SecurityFilterChain fixtureSecurity(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                    .build();
        }
    }

    static class TestActor implements CurrentActor, CurrentTenant {
        volatile Actor current = new Actor(TENANT, "analyst", UserRole.ANALYST);

        @Override
        public Actor actor() {
            return current;
        }

        @Override
        public TenantId tenantId() {
            return current.tenantId();
        }
    }
}
