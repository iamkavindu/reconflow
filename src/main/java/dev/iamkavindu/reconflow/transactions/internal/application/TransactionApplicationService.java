package dev.iamkavindu.reconflow.transactions.internal.application;

import dev.iamkavindu.reconflow.tenancy.Actor;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import dev.iamkavindu.reconflow.tenancy.CurrentTenant;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.RecordTransactionCommand;
import dev.iamkavindu.reconflow.transactions.TransactionCommands;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import dev.iamkavindu.reconflow.transactions.TransactionQueries;
import dev.iamkavindu.reconflow.transactions.TransactionView;
import dev.iamkavindu.reconflow.transactions.events.TransactionRecorded;
import dev.iamkavindu.reconflow.transactions.internal.domain.BusinessReference;
import dev.iamkavindu.reconflow.transactions.internal.domain.Money;
import dev.iamkavindu.reconflow.transactions.internal.domain.Transaction;
import dev.iamkavindu.reconflow.transactions.internal.domain.TransactionRepository;
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
public class TransactionApplicationService implements TransactionCommands, TransactionQueries {

    private final CurrentTenant currentTenant;
    private final CurrentActor currentActor;
    private final TransactionRepository transactionRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public TransactionApplicationService(
            CurrentTenant currentTenant,
            CurrentActor currentActor,
            TransactionRepository transactionRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this.currentTenant = currentTenant;
        this.currentActor = currentActor;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public TransactionView recordTransaction(RecordTransactionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String recordedBy = currentActor.optionalActor().map(Actor::subject).orElse(null);

        Transaction transaction = Transaction.record(
                new TransactionId(UUID.randomUUID()),
                currentTenant.tenantId(),
                new BusinessReference(command.businessReference()),
                command.type(),
                new Money(command.amount(), command.currency()),
                command.occurredAt(),
                Instant.now(clock).truncatedTo(ChronoUnit.MICROS));

        Transaction saved = transactionRepository.save(transaction);

        eventPublisher.publishEvent(new TransactionRecorded(
                UUID.randomUUID(),
                saved.tenantId(),
                saved.transactionId(),
                saved.businessReference().value(),
                saved.recordedAt(),
                UUID.randomUUID(),
                recordedBy));

        return toView(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionView getTransaction(TenantId tenantId, TransactionId transactionId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");

        return transactionRepository
                .findByTenantIdAndTransactionId(tenantId, transactionId)
                .map(TransactionApplicationService::toView)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found"));
    }

    private static TransactionView toView(Transaction transaction) {
        return new TransactionView(
                transaction.transactionId(),
                transaction.tenantId(),
                transaction.businessReference().value(),
                transaction.type(),
                transaction.money().amount(),
                transaction.money().currency().getCurrencyCode(),
                transaction.occurredAt(),
                transaction.status(),
                transaction.recordedAt());
    }
}
