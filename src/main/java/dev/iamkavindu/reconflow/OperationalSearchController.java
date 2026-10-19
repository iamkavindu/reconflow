package dev.iamkavindu.reconflow;

import dev.iamkavindu.reconflow.cases.CaseListing;
import dev.iamkavindu.reconflow.cases.CaseStatus;
import dev.iamkavindu.reconflow.cases.CaseView;
import dev.iamkavindu.reconflow.provider.ProviderTransactionListing;
import dev.iamkavindu.reconflow.provider.ProviderTransactionView;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationListing;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationStatus;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationView;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import dev.iamkavindu.reconflow.transactions.TransactionListing;
import dev.iamkavindu.reconflow.transactions.TransactionView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OperationalSearchController {
    private final TransactionListing transactions;
    private final ProviderTransactionListing providers;
    private final ReconciliationListing reconciliations;
    private final CaseListing cases;
    private final CurrentActor currentActor;

    OperationalSearchController(TransactionListing transactions, ProviderTransactionListing providers,
            ReconciliationListing reconciliations, CaseListing cases, CurrentActor currentActor) {
        this.transactions = transactions;
        this.providers = providers;
        this.reconciliations = reconciliations;
        this.cases = cases;
        this.currentActor = currentActor;
    }

    @GetMapping("/api/transactions")
    List<TransactionView> transactions(@RequestParam(required = false) UUID transactionId,
            @RequestParam(required = false) String reference, @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return transactions.search(currentActor.actor().tenantId(),
                new TransactionListing.Filter(transactionId, reference, from, to, limit, offset));
    }

    @GetMapping("/api/provider-transactions")
    List<ProviderTransactionView> providers(@RequestParam(required = false) UUID providerTransactionId,
            @RequestParam(required = false) String providerReference, @RequestParam(required = false) String reference,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) {
        return providers.search(currentActor.actor().tenantId(), new ProviderTransactionListing.Filter(
                providerTransactionId, providerReference, reference, from, to, limit, offset));
    }

    @GetMapping("/api/reconciliations")
    List<ReconciliationView> reconciliations(@RequestParam(required = false) UUID reconciliationId,
            @RequestParam(required = false) UUID transactionId, @RequestParam(required = false) UUID providerTransactionId,
            @RequestParam(required = false) String reference, @RequestParam(required = false) ReconciliationStatus status,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) {
        return reconciliations.search(currentActor.actor().tenantId(), new ReconciliationListing.Filter(
                reconciliationId, transactionId, providerTransactionId, reference, status, from, to, limit, offset));
    }

    @GetMapping("/api/cases")
    List<CaseView> cases(@RequestParam(required = false) UUID caseId,
            @RequestParam(required = false) UUID reconciliationId, @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) {
        return cases.search(currentActor.actor().tenantId(), new CaseListing.Filter(
                caseId, reconciliationId, status, from, to, limit, offset));
    }
}
