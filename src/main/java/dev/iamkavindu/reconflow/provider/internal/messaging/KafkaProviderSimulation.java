package dev.iamkavindu.reconflow.provider.internal.messaging;

import dev.iamkavindu.reconflow.provider.ProviderDeliveryUnavailableException;
import dev.iamkavindu.reconflow.provider.ProviderEventReceipt;
import dev.iamkavindu.reconflow.provider.ProviderSimulationCommands;
import dev.iamkavindu.reconflow.provider.SimulateProviderEventCommand;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderBusinessReference;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderEventId;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderMoney;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderReference;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.common.KafkaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
class KafkaProviderSimulation implements ProviderSimulationCommands {
    private static final String PROVIDER = "stripe";
    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;
    private final CurrentActor currentActor;
    private final Clock clock;
    private final String topic;
    private final boolean enabled;

    KafkaProviderSimulation(
            KafkaTemplate<String, String> kafka,
            JsonMapper json,
            CurrentActor currentActor,
            Clock clock,
            @Value("${reconflow.kafka.provider-events-topic}") String topic,
            @Value("${reconflow.kafka.enabled:true}") boolean enabled) {
        this.kafka = kafka;
        this.json = json;
        this.currentActor = currentActor;
        this.clock = clock;
        this.topic = topic;
        this.enabled = enabled;
    }

    @Override
    public boolean available() {
        return enabled;
    }

    @Override
    public ProviderEventReceipt simulate(SimulateProviderEventCommand command) {
        var actor = currentActor.actor();
        actor.requireAnalyst();
        if (!enabled) {
            throw new ProviderDeliveryUnavailableException();
        }
        Objects.requireNonNull(command);
        var eventId = new ProviderEventId(command.providerEventId());
        var reference = new ProviderReference(command.providerTransactionReference());
        var businessReference = new ProviderBusinessReference(command.businessReference());
        Objects.requireNonNull(command.amount());
        Objects.requireNonNull(command.currency());
        if ((long) command.amount().precision() - command.amount().scale() > 15
                || command.amount().stripTrailingZeros().scale()
                        > command.currency().getDefaultFractionDigits()) {
            throw new IllegalArgumentException(
                    "provider amount exceeds supported currency precision or storage bounds");
        }
        var money = new ProviderMoney(command.amount(), command.currency());
        var payload = new ProviderEventPayload(
                eventId.value(),
                PROVIDER,
                reference.value(),
                businessReference.value(),
                Objects.requireNonNull(command.type()),
                money.amount(),
                money.currency().getCurrencyCode(),
                Objects.requireNonNull(command.status()),
                Objects.requireNonNull(command.occurredAt()));
        UUID deliveryId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        var envelope = new ProviderEventEnvelope(
                ProviderEventMessageMapper.SCHEMA_VERSION,
                deliveryId,
                ProviderEventMessageMapper.EVENT_TYPE,
                now(),
                actor.tenantId().value(),
                correlationId,
                deliveryId,
                payload);
        String key = actor.tenantId().value() + "/" + PROVIDER + "/" + reference.value();
        try {
            kafka.send(topic, key, json.writeValueAsString(envelope)).get(6, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ProviderDeliveryUnavailableException();
        } catch (ExecutionException
                | TimeoutException
                | KafkaException
                | org.springframework.kafka.KafkaException unavailable) {
            throw new ProviderDeliveryUnavailableException();
        }
        return new ProviderEventReceipt(deliveryId, actor.tenantId(), eventId.value(), correlationId, now());
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
