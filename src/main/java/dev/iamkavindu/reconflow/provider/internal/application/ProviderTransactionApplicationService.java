package dev.iamkavindu.reconflow.provider.internal.application;

import dev.iamkavindu.reconflow.provider.IngestProviderEventCommand;
import dev.iamkavindu.reconflow.provider.ProviderTransactionCommands;
import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.provider.ProviderTransactionQueries;
import dev.iamkavindu.reconflow.provider.ProviderTransactionView;
import dev.iamkavindu.reconflow.provider.events.ProviderTransactionRecorded;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderBusinessReference;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderCode;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderEventId;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderMoney;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderReference;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderTransaction;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderTransactionRepository;
import dev.iamkavindu.reconflow.tenancy.CurrentTenant;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderTransactionApplicationService implements ProviderTransactionCommands, ProviderTransactionQueries {

    private final CurrentTenant currentTenant;
    private final ProviderTransactionRepository providerTransactionRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ProviderTransactionApplicationService(
            CurrentTenant currentTenant,
            ProviderTransactionRepository providerTransactionRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this.currentTenant = currentTenant;
        this.providerTransactionRepository = providerTransactionRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public ProviderTransactionView ingestProviderEvent(IngestProviderEventCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        ProviderTransaction providerTransaction = ProviderTransaction.record(
                new ProviderTransactionId(UUID.randomUUID()),
                currentTenant.tenantId(),
                new ProviderEventId(command.eventId()),
                new ProviderCode(command.provider()),
                new ProviderReference(command.providerTransactionReference()),
                new ProviderBusinessReference(command.businessReference()),
                command.type(),
                new ProviderMoney(command.amount(), command.currency()),
                command.status(),
                command.occurredAt(),
                Instant.now(clock).truncatedTo(ChronoUnit.MICROS),
                command.correlationId(),
                command.causationId());

        ProviderTransactionRepository.SaveResult result =
                providerTransactionRepository.saveIfAbsent(providerTransaction);

        if (result.created()) {
            eventPublisher.publishEvent(toEvent(result.providerTransaction()));
        }

        return toView(result.providerTransaction());
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderTransactionView getProviderTransaction(
            TenantId tenantId, ProviderTransactionId providerTransactionId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(providerTransactionId, "providerTransactionId must not be null");

        return providerTransactionRepository
                .findByTenantIdAndProviderTransactionId(tenantId, providerTransactionId)
                .map(ProviderTransactionApplicationService::toView)
                .orElseThrow(() -> new NoSuchElementException("Provider transaction not found"));
    }

    private static ProviderTransactionRecorded toEvent(ProviderTransaction providerTransaction) {
        return new ProviderTransactionRecorded(
                UUID.randomUUID(),
                providerTransaction.tenantId(),
                providerTransaction.providerTransactionId(),
                providerTransaction.provider().value(),
                providerTransaction.providerReference().value(),
                providerTransaction.businessReference().value(),
                providerTransaction.type(),
                providerTransaction.money().amount(),
                providerTransaction.money().currency().getCurrencyCode(),
                providerTransaction.status(),
                providerTransaction.occurredAt(),
                providerTransaction.recordedAt(),
                providerTransaction.correlationId(),
                providerTransaction.causationId());
    }

    private static ProviderTransactionView toView(ProviderTransaction providerTransaction) {
        return new ProviderTransactionView(
                providerTransaction.providerTransactionId(),
                providerTransaction.tenantId(),
                providerTransaction.providerEventId().value(),
                providerTransaction.provider().value(),
                providerTransaction.providerReference().value(),
                providerTransaction.businessReference().value(),
                providerTransaction.type(),
                providerTransaction.money().amount(),
                providerTransaction.money().currency().getCurrencyCode(),
                providerTransaction.status(),
                providerTransaction.occurredAt(),
                providerTransaction.recordedAt());
    }
}
