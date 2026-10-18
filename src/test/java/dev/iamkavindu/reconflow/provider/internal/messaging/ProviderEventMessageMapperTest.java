package dev.iamkavindu.reconflow.provider.internal.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ProviderEventMessageMapperTest {

    private final ProviderEventMessageMapper mapper = new ProviderEventMessageMapper(
            JsonMapper.builder().findAndAddModules().build());

    @Test
    void mapsVersionedEnvelopeToProviderIngestionCommand() {
        var command = mapper.toCommand(validMessage(1, ProviderEventMessageMapper.EVENT_TYPE));

        assertThat(command.tenantId().value()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(command.eventId()).isEqualTo("evt-1001");
        assertThat(command.provider()).isEqualTo("stripe");
        assertThat(command.providerTransactionReference()).isEqualTo("pi-1001");
        assertThat(command.businessReference()).isEqualTo("ORD-1001");
        assertThat(command.amount()).isEqualByComparingTo("100.00");
        assertThat(command.currency().getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        assertThatThrownBy(() -> mapper.toCommand(validMessage(2, ProviderEventMessageMapper.EVENT_TYPE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported provider event schema version");
    }

    @Test
    void rejectsUnsupportedEventType() {
        assertThatThrownBy(() -> mapper.toCommand(validMessage(1, "provider.unknown.v1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported provider event type");
    }

    private static String validMessage(int schemaVersion, String eventType) {
        return """
                {
                  "schemaVersion": %d,
                  "eventId": "11111111-1111-1111-1111-111111111111",
                  "eventType": "%s",
                  "occurredAt": "2026-10-10T09:25:01Z",
                  "tenantId": "00000000-0000-0000-0000-000000000001",
                  "correlationId": "22222222-2222-2222-2222-222222222222",
                  "causationId": "33333333-3333-3333-3333-333333333333",
                  "payload": {
                    "providerEventId": "evt-1001",
                    "provider": "stripe",
                    "providerTransactionReference": "pi-1001",
                    "businessReference": "ORD-1001",
                    "type": "PAYMENT",
                    "amount": 100.00,
                    "currency": "USD",
                    "status": "SUCCEEDED",
                    "occurredAt": "2026-10-10T09:25:00Z"
                  }
                }
                """.formatted(schemaVersion, eventType);
    }
}
