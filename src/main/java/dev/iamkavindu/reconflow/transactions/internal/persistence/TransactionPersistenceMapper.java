package dev.iamkavindu.reconflow.transactions.internal.persistence;

import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import dev.iamkavindu.reconflow.transactions.internal.domain.BusinessReference;
import dev.iamkavindu.reconflow.transactions.internal.domain.Money;
import dev.iamkavindu.reconflow.transactions.internal.domain.Transaction;
import java.util.Currency;

final class TransactionPersistenceMapper {

    private TransactionPersistenceMapper() {}

    static TransactionJpaEntity toEntity(Transaction transaction) {
        return new TransactionJpaEntity(
                transaction.transactionId().value(),
                transaction.tenantId().value(),
                transaction.businessReference().value(),
                transaction.type(),
                transaction.money().amount(),
                transaction.money().currency().getCurrencyCode(),
                transaction.occurredAt(),
                transaction.status(),
                transaction.recordedAt());
    }

    static Transaction toDomain(TransactionJpaEntity entity) {
        return Transaction.record(
                new TransactionId(entity.id()),
                new TenantId(entity.tenantId()),
                new BusinessReference(entity.businessReference()),
                entity.type(),
                new Money(entity.amount(), Currency.getInstance(entity.currency())),
                entity.occurredAt(),
                entity.recordedAt());
    }
}
