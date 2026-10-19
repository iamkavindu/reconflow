package dev.iamkavindu.reconflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.iamkavindu.reconflow.transactions.RecordTransactionCommand;
import dev.iamkavindu.reconflow.transactions.TransactionCommands;
import dev.iamkavindu.reconflow.transactions.events.TransactionRecorded;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class EventRecoveryIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.7-pg18-bookworm").asCompatibleSubstituteFor("postgres"));

    static final AtomicBoolean UNAVAILABLE = new AtomicBoolean(true);
    static final AtomicInteger ATTEMPTS = new AtomicInteger();

    @Test
    void replaysAnUnfinishedPublicationAfterRestartWithoutTheAiProfile() {
        UNAVAILABLE.set(true);
        ATTEMPTS.set(0);
        UUID transactionId;
        try (var first = start()) {
            var transaction = first.getBean(TransactionCommands.class).recordTransaction(new RecordTransactionCommand(
                    "RESTART-" + UUID.randomUUID(),
                    dev.iamkavindu.reconflow.transactions.TransactionType.PAYMENT,
                    new BigDecimal("100.00"),
                    Currency.getInstance("USD"),
                    Instant.now()));
            transactionId = transaction.transactionId().value();
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(ATTEMPTS.get()).isEqualTo(1);
                assertThat(publications(first, false)).isEqualTo(1);
            });
        }

        UNAVAILABLE.set(false);
        try (var restarted = start()) {
            assertThat(restarted.getEnvironment().getActiveProfiles()).doesNotContain("ai");
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertThat(ATTEMPTS.get()).isEqualTo(2);
                assertThat(publications(restarted, false)).isZero();
                assertThat(publications(restarted, true)).isEqualTo(1);
            });
            assertThat(restarted.getBean(JdbcClient.class)
                    .sql("select count(*) from transactions.transactions where id = :id")
                    .param("id", transactionId)
                    .query(Long.class)
                    .single()).isEqualTo(1);
        }
    }

    private static ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(ReconFlowApplication.class, RecoveryConfiguration.class)
                .run(
                        "--spring.main.web-application-type=none",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--reconflow.security.enabled=false",
                        "--reconflow.kafka.enabled=false",
                        "--reconflow.reconciliation.scheduling-enabled=false");
    }

    private static long publications(ConfigurableApplicationContext context, boolean completed) {
        return context.getBean(JdbcClient.class)
                .sql("select count(*) from event_publication where listener_id like :listener and completion_date is "
                        + (completed ? "not null" : "null"))
                .param("listener", "%RecoveryListener%")
                .query(Long.class)
                .single();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RecoveryConfiguration {
        @Bean
        RecoveryListener recoveryListener() {
            return new RecoveryListener();
        }
    }

    static class RecoveryListener {
        @ApplicationModuleListener
        void on(TransactionRecorded event) {
            ATTEMPTS.incrementAndGet();
            if (UNAVAILABLE.get()) {
                throw new IllegalStateException("simulated unavailable projection");
            }
        }
    }
}
