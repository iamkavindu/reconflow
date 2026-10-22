package dev.iamkavindu.reconflow.ai.internal;

import dev.iamkavindu.reconflow.ai.KnowledgeEvidence;
import dev.iamkavindu.reconflow.ai.KnowledgeKind;
import dev.iamkavindu.reconflow.ai.KnowledgeSearch;
import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.CaseQueries;
import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.provider.ProviderTransactionQueries;
import dev.iamkavindu.reconflow.provider.ProviderTransactionView;
import dev.iamkavindu.reconflow.provider.SettlementLine;
import dev.iamkavindu.reconflow.provider.SettlementQueries;
import dev.iamkavindu.reconflow.reconciliation.Discrepancy;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationQueries;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationView;
import dev.iamkavindu.reconflow.reconciliation.RuleEvaluation;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.transactions.TransactionQueries;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.ai.tool.annotation.Tool;
import tools.jackson.databind.json.JsonMapper;

/** Per-investigation tools capture trusted tenant and case identities, never model-supplied scope. */
class InvestigationReadTools {

    private final TenantId tenantId;
    private final ReconciliationView reconciliation;
    private final TransactionQueries transactions;
    private final ProviderTransactionQueries providers;
    private final SettlementQueries settlementEvidence;
    private final KnowledgeSearch knowledge;
    private final JsonMapper json;
    private final Map<String, String> cache = new LinkedHashMap<>();
    private final Set<String> references = new LinkedHashSet<>();
    private final Map<KnowledgeKind, Set<String>> documentationReferences = new LinkedHashMap<>();
    private final List<String> limitations = new ArrayList<>();
    private final List<ProviderTransactionId> providerIds;
    private boolean hasPolicyEvidence;
    private List<ProviderTransactionView> providerSnapshot;

    InvestigationReadTools(
            TenantId tenantId,
            CaseId caseId,
            CaseQueries cases,
            ReconciliationQueries reconciliations,
            TransactionQueries transactions,
            ProviderTransactionQueries providers,
            SettlementQueries settlementEvidence,
            KnowledgeSearch knowledge,
            JsonMapper json) {
        this.tenantId = tenantId;
        this.transactions = transactions;
        this.providers = providers;
        this.settlementEvidence = settlementEvidence;
        this.knowledge = knowledge;
        this.json = json;
        var investigationCase = cases.getCase(tenantId, caseId);
        this.reconciliation = reconciliations.getReconciliationById(tenantId, investigationCase.reconciliationId());
        var ids = new LinkedHashSet<>(reconciliation.comparedProviderTransactionIds());
        if (reconciliation.providerOriginId() != null) {
            ids.add(reconciliation.providerOriginId());
        }
        this.providerIds = ids.stream().limit(10).toList();
        if (ids.size() > 10) {
            limitations.add("Only the first ten provider source records were included in this bounded investigation.");
        }
        references.add("case:" + caseId.value());
        cache.put(
                "case",
                json.writeValueAsString(Map.of(
                        "reference",
                        "case:" + caseId.value(),
                        "status",
                        investigationCase.status(),
                        "reconciliationId",
                        investigationCase.reconciliationId())));
        getReconciliation();
        getTransaction();
        getProviderTransaction();
        getSettlement();
        getDiscrepancies();
    }

    @Tool(description = "Read the immutable reconciliation determination for this investigation's bound case")
    String getReconciliation() {
        return cached("reconciliation", () -> {
            String reference =
                    "reconciliation:" + reconciliation.reconciliationId().value();
            references.add(reference);
            return new Evidence(
                    reference,
                    new ReconciliationContext(
                            reconciliation.reconciliationId().value(),
                            reconciliation.status().name(),
                            reconciliation.businessReference(),
                            reconciliation.startedAt(),
                            reconciliation.expiresAt(),
                            reconciliation.decidedAt(),
                            reconciliation.ruleEvaluations(),
                            reconciliation.discrepancies(),
                            providerIds));
        });
    }

    @Tool(
            description =
                    "Read the internal transaction already associated with the bound case; no arbitrary IDs are accepted")
    String getTransaction() {
        return cached("transaction", () -> {
            if (reconciliation.transactionId() == null) {
                limitations.add("No internal transaction was linked to this reconciliation.");
                return Map.of("available", false);
            }
            var record = transactions.getTransaction(tenantId, reconciliation.transactionId());
            String reference = "transaction:" + record.transactionId().value();
            references.add(reference);
            return new Evidence(reference, record);
        });
    }

    @Tool(description = "Read up to ten normalized provider transaction records already linked to the bound case")
    String getProviderTransaction() {
        return cached(
                "provider",
                () -> providerRecords().stream()
                        .map(record -> {
                            String reference = "provider-transaction:"
                                    + record.providerTransactionId().value();
                            references.add(reference);
                            return new Evidence(reference, record);
                        })
                        .toList());
    }

    @Tool(
            description =
                    "Read reported settlement totals and case-relevant lines; these do not establish contractual fee correctness")
    String getSettlement() {
        return cached("settlement", () -> {
            var records = providerRecords();
            var settlements = providerIds.stream()
                    .flatMap(id -> settlementEvidence.forProviderTransaction(tenantId, id).stream())
                    .distinct()
                    .toList();
            if (settlements.size() > 10) {
                limitations.add("Only the first ten linked settlements were included.");
            }
            return settlements.stream()
                    .limit(10)
                    .map(settlement -> {
                        String reference =
                                "settlement:" + settlement.settlementId().value();
                        references.add(reference);
                        var sourceRefs = records.stream()
                                .filter(record -> record.provider().equals(settlement.provider()))
                                .map(ProviderTransactionView::providerTransactionReference)
                                .toList();
                        var lines = settlement.lines().stream()
                                .filter(line -> sourceRefs.contains(line.providerTransactionReference()))
                                .limit(10)
                                .toList();
                        return new Evidence(
                                reference,
                                new SettlementContext(
                                        settlement.provider(),
                                        settlement.currency(),
                                        settlement.grossAmount(),
                                        settlement.feeAmount(),
                                        settlement.netAmount(),
                                        lines));
                    })
                    .toList();
        });
    }

    @Tool(description = "Read deterministic discrepancies; findings cannot be changed or resolved by these tools")
    String getDiscrepancies() {
        return cached(
                "discrepancies",
                () -> reconciliation.discrepancies().stream()
                        .map(discrepancy -> {
                            String reference = "discrepancy:" + discrepancy.discrepancyId();
                            references.add(reference);
                            return new Evidence(reference, discrepancy);
                        })
                        .toList());
    }

    @Tool(
            description =
                    "Search screened policy evidence within the bound tenant; query text cannot change tenant scope")
    String searchPolicies(String query) {
        return search(KnowledgeKind.POLICY, query);
    }

    @Tool(description = "Search screened provider documentation within the bound tenant")
    String searchProviderDocumentation(String query) {
        return search(KnowledgeKind.PROVIDER_DOCUMENTATION, query);
    }

    @Tool(
            description =
                    "Search screened past-case documentation within the bound tenant; examples are supporting context only")
    String searchSimilarCases(String query) {
        return search(KnowledgeKind.PAST_CASE, query);
    }

    synchronized List<String> context() {
        return List.copyOf(cache.values());
    }

    synchronized Set<String> references() {
        return Set.copyOf(references);
    }

    void retrieveDocumentation() {
        String providers = providerRecords().stream()
                .map(ProviderTransactionView::provider)
                .distinct()
                .limit(10)
                .collect(java.util.stream.Collectors.joining(" "));
        String discrepancies = reconciliation.discrepancies().stream()
                .map(value -> value.type().name())
                .distinct()
                .collect(java.util.stream.Collectors.joining(" "));
        String query = "Reconciliation human review policy and provider documentation for " + discrepancies + " "
                + providers + " gross net settlement fees and record errors";
        query = query.substring(0, Math.min(1000, query.length()));
        searchPolicies(query);
        searchProviderDocumentation(query);
    }

    synchronized String citationFeedback(List<dev.iamkavindu.reconflow.cases.InvestigationEvidence> evidence) {
        var applicable = documentationReferences.values().stream()
                .flatMap(Set::stream)
                .collect(java.util.stream.Collectors.toSet());
        if (!applicable.isEmpty() && evidence.stream().noneMatch(value -> applicable.contains(value.reference()))) {
            return "Relevant documentation was supplied, but the recommendation does not cite it. "
                    + "Cite an exact supplied policy or provider documentation reference and explain what that "
                    + "passage supports. Do not invent references or contractual terms.";
        }
        return null;
    }

    synchronized List<String> limitations() {
        var result = new ArrayList<>(limitations);
        if (!hasPolicyEvidence) {
            result.add(
                    "No screened policy or provider documentation was available to verify contractual fee correctness.");
        }
        return List.copyOf(result);
    }

    private String search(KnowledgeKind kind, String query) {
        if (query == null || query.isBlank() || query.length() > 1000) {
            throw new IllegalArgumentException("knowledge queries must contain at most 1000 characters");
        }
        return cached("knowledge:" + kind, () -> {
            List<KnowledgeEvidence> results = knowledge.search(tenantId, kind, query);
            results.forEach(result -> references.add(result.reference()));
            if (kind != KnowledgeKind.PAST_CASE) {
                documentationReferences.put(
                        kind,
                        results.stream()
                                .map(KnowledgeEvidence::reference)
                                .collect(java.util.stream.Collectors.toSet()));
            }
            hasPolicyEvidence |= !results.isEmpty() && kind != KnowledgeKind.PAST_CASE;
            return results;
        });
    }

    private List<ProviderTransactionView> providerRecords() {
        if (providerSnapshot == null) {
            providerSnapshot = providerIds.stream()
                    .map(id -> providers.getProviderTransaction(tenantId, id))
                    .toList();
        }
        return providerSnapshot;
    }

    private synchronized String cached(String name, Supplier<Object> read) {
        if (cache.containsKey(name)) {
            return cache.get(name);
        }
        String result = json.writeValueAsString(read.get());
        int currentSize = cache.values().stream().mapToInt(String::length).sum();
        if (result.length() > 30000 || currentSize + result.length() > 80000) {
            throw new IllegalStateException("financial context exceeds the bounded investigation budget");
        }
        cache.put(name, result);
        return result;
    }

    private record Evidence(String reference, Object data) {}

    private record ReconciliationContext(
            UUID id,
            String status,
            String businessReference,
            Instant startedAt,
            Instant expiresAt,
            Instant decidedAt,
            List<RuleEvaluation> rules,
            List<Discrepancy> discrepancies,
            List<ProviderTransactionId> providerTransactionIds) {}

    private record SettlementContext(
            String provider,
            String currency,
            BigDecimal grossAmount,
            BigDecimal feeAmount,
            BigDecimal netAmount,
            List<SettlementLine> relevantLines) {}
}
