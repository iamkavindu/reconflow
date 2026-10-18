package dev.iamkavindu.reconflow.transactions.internal.application;

import dev.iamkavindu.reconflow.tenancy.CurrentTenant;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.RecordTransactionCommand;
import dev.iamkavindu.reconflow.transactions.TransactionCommands;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import dev.iamkavindu.reconflow.transactions.TransactionQueries;
import dev.iamkavindu.reconflow.transactions.TransactionView;
import dev.iamkavindu.reconflow.transactions.internal.domain.BusinessReference;
import dev.iamkavindu.reconflow.transactions.internal.domain.Money;
import dev.iamkavindu.reconflow.transactions.internal.domain.Transaction;
import dev.iamkavindu.reconflow.transactions.internal.domain.TransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionApplicationService implements TransactionCommands, TransactionQueries {

    private final CurrentTenant currentTenant;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public TransactionApplicationService(
            CurrentTenant currentTenant, TransactionRepository transactionRepository, Clock clock) {
        this.currentTenant = currentTenant;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TransactionView recordTransaction(RecordTransactionCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Transaction transaction = Transaction.record(
                new TransactionId(UUID.randomUUID()),
                currentTenant.tenantId(),
                new BusinessReference(command.businessReference()),
                command.type(),
                new Money(command.amount(), command.currency()),
                command.occurredAt(),
                Instant.now(clock));

        return toView(transactionRepository.save(transaction));
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
