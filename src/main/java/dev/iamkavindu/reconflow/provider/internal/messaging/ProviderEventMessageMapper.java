package dev.iamkavindu.reconflow.provider.internal.messaging;

import dev.iamkavindu.reconflow.provider.IngestProviderEventCommand;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.Currency;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

final class ProviderEventMessageMapper {

    static final int SCHEMA_VERSION = 1;
    static final String EVENT_TYPE = "provider.transaction.recorded.v1";

    private final JsonMapper jsonMapper;

    ProviderEventMessageMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    IngestProviderEventCommand toCommand(String message) {
        ProviderEventEnvelope envelope;
        try {
            envelope = jsonMapper.readValue(message, ProviderEventEnvelope.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("provider event must be valid JSON", exception);
        }

        validateEnvelope(envelope);
        ProviderEventPayload payload = envelope.payload();

        return new IngestProviderEventCommand(
                new TenantId(envelope.tenantId()),
                payload.providerEventId(),
                payload.provider(),
                payload.providerTransactionReference(),
                payload.businessReference(),
                payload.type(),
                payload.amount(),
                Currency.getInstance(payload.currency()),
                payload.status(),
                payload.occurredAt(),
                envelope.correlationId(),
                envelope.causationId());
    }

    private static void validateEnvelope(ProviderEventEnvelope envelope) {
        Objects.requireNonNull(envelope, "provider event envelope must not be null");
        if (envelope.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported provider event schema version");
        }
        if (!EVENT_TYPE.equals(envelope.eventType())) {
            throw new IllegalArgumentException("unsupported provider event type");
        }
        Objects.requireNonNull(envelope.eventId(), "eventId must not be null");
        Objects.requireNonNull(envelope.occurredAt(), "occurredAt must not be null");
        Objects.requireNonNull(envelope.tenantId(), "tenantId must not be null");
        Objects.requireNonNull(envelope.correlationId(), "correlationId must not be null");
        Objects.requireNonNull(envelope.causationId(), "causationId must not be null");
        Objects.requireNonNull(envelope.payload(), "payload must not be null");
    }
}
