package dev.iamkavindu.reconflow.provider.internal.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.iamkavindu.reconflow.provider.ProviderDeliveryUnavailableException;
import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import dev.iamkavindu.reconflow.provider.SimulateProviderEventCommand;
import dev.iamkavindu.reconflow.tenancy.Actor;
import dev.iamkavindu.reconflow.tenancy.OperationNotAuthorizedException;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.tenancy.UserRole;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

class KafkaProviderSimulationTest {
    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
    private final JsonMapper json = JsonMapper.builder().build();

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

    @Test
    void publishesCanonicalVersionedEvidenceWithTrustedScopeAndStableStreamKey() {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        var simulation = simulation(UserRole.ANALYST);
        var first = simulation.simulate(command("100.00"));
        var key = ArgumentCaptor.forClass(String.class);
        var value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("provider-test"), key.capture(), value.capture());
        assertThat(key.getValue()).isEqualTo(TENANT.value() + "/stripe/pi-demo");
        var ingested = new ProviderEventMessageMapper(json).toCommand(value.getValue());
        assertThat(ingested.tenantId()).isEqualTo(TENANT);
        assertThat(ingested.eventId()).isEqualTo("evt-demo");
        assertThat(ingested.providerTransactionReference()).isEqualTo("pi-demo");
        assertThat(ingested.amount()).isEqualByComparingTo("100.00");
        assertThat(first.acceptedAt()).isEqualTo(NOW);
        assertThat(first.correlationId()).isEqualTo(ingested.correlationId());
    }

    @Test
    void rejectsViewersAndUnsupportedMoneyBeforePublishing() {
        assertThatThrownBy(() -> simulation(UserRole.VIEWER).simulate(command("100.00")))
                .isInstanceOf(OperationNotAuthorizedException.class);
        for (String amount : new String[] {"0", "-1", "1.001", "1e2147483647"}) {
            assertThatThrownBy(() -> simulation(UserRole.ADMIN).simulate(command(amount)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(kafka);
    }

    @Test
    void reportsUncertainDeliveryWithoutLeakingBrokerDetails() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("sensitive broker details")));
        assertThatThrownBy(() -> simulation(UserRole.ANALYST).simulate(command("100.00")))
                .isInstanceOf(ProviderDeliveryUnavailableException.class)
                .hasMessageNotContaining("sensitive");
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenThrow(new org.springframework.kafka.KafkaException("sensitive broker details"));
        assertThatThrownBy(() -> simulation(UserRole.ADMIN).simulate(command("100.00")))
                .isInstanceOf(ProviderDeliveryUnavailableException.class)
                .hasMessageNotContaining("sensitive");
    }

    private KafkaProviderSimulation simulation(UserRole role) {
        return new KafkaProviderSimulation(
                kafka,
                json,
                () -> new Actor(TENANT, "operator", role),
                Clock.fixed(NOW, ZoneOffset.UTC),
                "provider-test",
                true);
    }

    private SimulateProviderEventCommand command(String amount) {
        return new SimulateProviderEventCommand(
                " evt-demo ",
                " pi-demo ",
                " ORD-1001 ",
                ProviderTransactionType.PAYMENT,
                new BigDecimal(amount),
                Currency.getInstance("USD"),
                ProviderTransactionStatus.SUCCEEDED,
                NOW);
    }
}
