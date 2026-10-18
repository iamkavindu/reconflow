package dev.iamkavindu.reconflow.reconciliation.internal.domain;

import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionView;
import dev.iamkavindu.reconflow.reconciliation.RuleEvaluation;
import dev.iamkavindu.reconflow.transactions.TransactionStatus;
import dev.iamkavindu.reconflow.transactions.TransactionView;
import java.time.Duration;
import java.util.List;

public final class ReconciliationEvaluator {

    public List<RuleEvaluation> evaluate(
            TransactionView transaction, ProviderTransactionView providerTransaction, Duration maximumTimeDifference) {
        if (!transaction.tenantId().equals(providerTransaction.tenantId())) {
            throw new IllegalArgumentException("reconciliation records must belong to the same tenant");
        }
        Duration difference = Duration.between(transaction.occurredAt(), providerTransaction.occurredAt())
                .abs();
        return List.of(
                equal("REFERENCE", transaction.businessReference(), providerTransaction.businessReference()),
                new RuleEvaluation(
                        "AMOUNT",
                        transaction.amount().compareTo(providerTransaction.amount()) == 0,
                        transaction.amount().toPlainString(),
                        providerTransaction.amount().toPlainString()),
                equal("CURRENCY", transaction.currency(), providerTransaction.currency()),
                equal(
                        "TYPE",
                        transaction.type().name(),
                        providerTransaction.type().name()),
                new RuleEvaluation(
                        "STATUS",
                        transaction.status() == TransactionStatus.RECORDED
                                && providerTransaction.status() == ProviderTransactionStatus.SUCCEEDED,
                        "RECORDED/SUCCEEDED",
                        transaction.status() + "/" + providerTransaction.status()),
                new RuleEvaluation(
                        "TIME_DIFFERENCE",
                        difference.compareTo(maximumTimeDifference) <= 0,
                        maximumTimeDifference.toString(),
                        difference.toString()));
    }

    private static RuleEvaluation equal(String rule, String expected, String actual) {
        return new RuleEvaluation(rule, expected.equals(actual), expected, actual);
    }
}
