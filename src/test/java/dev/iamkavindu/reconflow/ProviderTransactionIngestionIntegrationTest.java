package dev.iamkavindu.reconflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.iamkavindu.reconflow.provider.IngestProviderEventCommand;
import dev.iamkavindu.reconflow.provider.ProviderTransactionCommands;
import dev.iamkavindu.reconflow.provider.ProviderTransactionQueries;
import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import dev.iamkavindu.reconflow.provider.ProviderTransactionView;
import dev.iamkavindu.reconflow.provider.events.ProviderTransactionRecorded;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "reconflow.kafka.enabled=false")
@RecordApplicationEvents
class ProviderTransactionIngestionIntegrationTest {

    private static final TenantId CONFIGURED_TENANT =
            new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Autowired
    private ProviderTransactionCommands commands;

    @Autowired
    private ProviderTransactionQueries queries;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Test
    void persistsOneLogicalProviderTransactionForDuplicateDelivery() {
        IngestProviderEventCommand command = new IngestProviderEventCommand(
                CONFIGURED_TENANT,
                "evt-integration-1001",
                "stripe",
                "pi-integration-1001",
                "ORD-INTEGRATION-1001",
                ProviderTransactionType.PAYMENT,
                new BigDecimal("100.00"),
                Currency.getInstance("USD"),
                ProviderTransactionStatus.SUCCEEDED,
                Instant.parse("2026-10-10T09:25:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID());

        ProviderTransactionView first = commands.ingestProviderEvent(command);
        ProviderTransactionView duplicate = commands.ingestProviderEvent(command);
        ProviderTransactionView reloaded =
                queries.getProviderTransaction(CONFIGURED_TENANT, first.providerTransactionId());

        assertThat(duplicate).isEqualTo(first);
        assertThat(reloaded).isEqualTo(first);
        assertThat(applicationEvents.stream(ProviderTransactionRecorded.class)).hasSize(1);
    }
}
