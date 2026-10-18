package dev.iamkavindu.reconflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.iamkavindu.reconflow.provider.events.ProviderTransactionRecorded;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "reconflow.kafka.enabled=true")
@RecordApplicationEvents
@Testcontainers
class ProviderEventKafkaIntegrationTest {

    private static final String TOPIC = "reconflow.provider-events.v1";

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.0");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Test
    void consumesVersionedProviderEventIntoIdempotentProviderBoundary() throws Exception {
        String providerEventId = "evt-kafka-" + UUID.randomUUID();

        kafkaTemplate
                .send(TOPIC, providerEventId, validMessage(providerEventId))
                .get();

        await(() -> providerTransactionCount(providerEventId) == 1, Duration.ofSeconds(10));

        assertThat(applicationEvents.stream(ProviderTransactionRecorded.class)
                        .filter(event -> event.providerTransactionReference().equals("pi-" + providerEventId)))
                .hasSize(1);
    }

    @Test
    void routesUnsupportedEnvelopeToDeadLetterTopicAfterBoundedRetries() throws Exception {
        String invalidMessage = validMessage("evt-invalid-" + UUID.randomUUID())
                .replace("\"schemaVersion\": 1", "\"schemaVersion\": 2");

        try (var consumer = new KafkaConsumer<String, String>(consumerProperties())) {
            consumer.subscribe(List.of(TOPIC + "-dlt"));
            kafkaTemplate.send(TOPIC, "invalid-schema", invalidMessage).get();

            String deadLetterValue = awaitRecordValue(consumer, invalidMessage, Duration.ofSeconds(15));

            assertThat(deadLetterValue).isEqualTo(invalidMessage);
        }
    }

    private int providerTransactionCount(String providerEventId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM provider.provider_transactions WHERE provider_event_id = ?",
                Integer.class,
                providerEventId);
    }

    private static Map<String, Object> consumerProperties() {
        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "provider-dlt-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.CLIENT_ID_CONFIG,
                "provider-dlt-test");
    }

    private static String awaitRecordValue(
            KafkaConsumer<String, String> consumer, String expectedValue, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            for (var record : consumer.poll(Duration.ofMillis(250))) {
                if (expectedValue.equals(record.value())) {
                    return record.value();
                }
            }
        }
        throw new AssertionError("provider event was not routed to the dead-letter topic within " + timeout);
    }

    private static void await(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("condition was not met within " + timeout);
    }

    private static String validMessage(String providerEventId) {
        return """
                {
                  "schemaVersion": 1,
                  "eventId": "%s",
                  "eventType": "provider.transaction.recorded.v1",
                  "occurredAt": "2026-10-10T09:25:01Z",
                  "tenantId": "00000000-0000-0000-0000-000000000001",
                  "correlationId": "22222222-2222-2222-2222-222222222222",
                  "causationId": "33333333-3333-3333-3333-333333333333",
                  "payload": {
                    "providerEventId": "%s",
                    "provider": "stripe",
                    "providerTransactionReference": "pi-%s",
                    "businessReference": "ORD-KAFKA-1001",
                    "type": "PAYMENT",
                    "amount": 100.00,
                    "currency": "USD",
                    "status": "SUCCEEDED",
                    "occurredAt": "2026-10-10T09:25:00Z"
                  }
                }
                """
                .formatted(UUID.randomUUID(), providerEventId, providerEventId);
    }
}
