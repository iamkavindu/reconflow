package dev.iamkavindu.reconflow.ai.internal;

import dev.iamkavindu.reconflow.ai.KnowledgeDocuments;
import dev.iamkavindu.reconflow.ai.KnowledgeEvidence;
import dev.iamkavindu.reconflow.ai.KnowledgeKind;
import dev.iamkavindu.reconflow.ai.KnowledgeSearch;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springaicommunity.typesafe.rag.JevDocumentFilter;
import org.springaicommunity.typesafe.rag.JevDocumentReranker;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = {"reconflow.ai.enabled", "reconflow.ai.knowledge-enabled"},
        havingValue = "true")
class TenantKnowledgeSearch implements KnowledgeSearch {

    private final PgVectorStore vectors;
    private final KnowledgeDocuments sources;
    private final RewriteQueryTransformer rewriter;
    private final MultiQueryExpander expander;
    private final JevDocumentFilter filter;
    private final JevDocumentReranker reranker;

    TenantKnowledgeSearch(
            PgVectorStore vectors,
            KnowledgeDocuments sources,
            RewriteQueryTransformer rewriter,
            MultiQueryExpander expander,
            JevDocumentFilter filter,
            JevDocumentReranker reranker) {
        this.vectors = vectors;
        this.sources = sources;
        this.rewriter = rewriter;
        this.expander = expander;
        this.filter = filter;
        this.reranker = reranker;
    }

    @Override
    public List<KnowledgeEvidence> search(TenantId tenantId, KnowledgeKind kind, String text) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(kind);
        var query = bounded(text);
        var rewritten = bounded(rewriter.transform(query).text());
        var expressions = new FilterExpressionBuilder();
        var scope = expressions
                .and(expressions.eq("tenantId", tenantId.value().toString()), expressions.eq("kind", kind.name()))
                .build();
        var retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectors)
                .topK(4)
                .similarityThreshold(0.5)
                .filterExpression(scope)
                .build();
        Map<String, Document> candidates = new LinkedHashMap<>();
        var variants = new ArrayList<>(List.of(query, rewritten));
        variants.addAll(expander.expand(rewritten).stream()
                .limit(4)
                .map(value -> bounded(value.text()))
                .toList());
        // Fresh queries have no model-controlled context capable of overriding the tenant filter.
        for (var variant :
                variants.stream().map(Query::text).distinct().limit(5).toList()) {
            for (var document : retriever.retrieve(new Query(variant))) {
                var passage = sources.passage(tenantId, UUID.fromString(document.getId()));
                if (passage.kind() == kind) {
                    // Rebuild from immutable sources; discard all stored judgment metadata.
                    candidates.putIfAbsent(
                            document.getId(),
                            Document.builder()
                                    .id(document.getId())
                                    .text(passage.content())
                                    .metadata(Map.of("title", passage.title()))
                                    .build());
                }
            }
        }
        List<Document> screened = filter
                .process(query, candidates.values().stream().limit(12).toList())
                .stream()
                .filter(value -> classified(value.getMetadata().get(JevDocumentFilter.CLASSIFICATION_METADATA_KEY)))
                .toList();
        return reranker.process(query, screened).stream()
                .filter(value -> {
                    Object score = value.getMetadata().get(JevDocumentReranker.SCORE_METADATA_KEY);
                    return score instanceof Number number
                            && Double.isFinite(number.doubleValue())
                            && number.doubleValue() >= 0.5
                            && number.doubleValue() <= 1;
                })
                .limit(3)
                .map(value -> new KnowledgeEvidence(
                        "document-chunk:" + value.getId(),
                        (String) value.getMetadata().get("title"),
                        value.getText(),
                        value.getMetadata()
                                .get(JevDocumentFilter.CLASSIFICATION_METADATA_KEY)
                                .toString(),
                        ((Number) value.getMetadata().get(JevDocumentReranker.SCORE_METADATA_KEY)).doubleValue()))
                .toList();
    }

    private static boolean classified(Object value) {
        return "INCLUDED".equals(value) || "CONFLICTING".equals(value);
    }

    private static Query bounded(String value) {
        if (value == null || value.isBlank() || value.length() > 1000) {
            throw new IllegalArgumentException("knowledge queries require at most 1000 characters");
        }
        return new Query(value);
    }
}
