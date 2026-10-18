package dev.iamkavindu.reconflow.provider.internal.messaging;

import java.time.Instant;
import java.util.UUID;

record ProviderEventEnvelope(
        int schemaVersion,
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID tenantId,
        UUID correlationId,
        UUID causationId,
        ProviderEventPayload payload) {}
