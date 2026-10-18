package dev.iamkavindu.reconflow.reconciliation.internal.web;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationQueries;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationView;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReconciliationController {

    private final ReconciliationQueries queries;
    private final CurrentActor currentActor;

    ReconciliationController(ReconciliationQueries queries, CurrentActor currentActor) {
        this.queries = queries;
        this.currentActor = currentActor;
    }

    @GetMapping("/api/reconciliations/{id}")
    ReconciliationView get(@PathVariable UUID id) {
        return queries.getReconciliationById(currentActor.actor().tenantId(), new ReconciliationId(id));
    }

    @GetMapping("/api/transactions/{id}/reconciliation")
    ReconciliationView forTransaction(@PathVariable UUID id) {
        return queries.getReconciliation(currentActor.actor().tenantId(), new TransactionId(id));
    }

    @GetMapping("/api/provider-transactions/{id}/reconciliation")
    ReconciliationView forProviderTransaction(@PathVariable UUID id) {
        return queries.getProviderReconciliation(currentActor.actor().tenantId(), new ProviderTransactionId(id));
    }
}
