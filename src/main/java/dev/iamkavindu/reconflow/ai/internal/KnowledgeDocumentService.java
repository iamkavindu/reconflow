package dev.iamkavindu.reconflow.ai.internal;

import dev.iamkavindu.reconflow.ai.ImportKnowledgeDocument;
import dev.iamkavindu.reconflow.ai.KnowledgeDocument;
import dev.iamkavindu.reconflow.ai.KnowledgeDocuments;
import dev.iamkavindu.reconflow.ai.KnowledgeKind;
import dev.iamkavindu.reconflow.ai.KnowledgePassage;
import dev.iamkavindu.reconflow.ai.events.KnowledgeDocumentImported;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class KnowledgeDocumentService implements KnowledgeDocuments {

    private final JdbcClient jdbc;
    private final ObjectProvider<PgVectorStore> vectors;
    private final CurrentActor currentActor;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    KnowledgeDocumentService(
            JdbcClient jdbc,
            ObjectProvider<PgVectorStore> vectors,
            CurrentActor currentActor,
            Clock clock,
            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.vectors = vectors;
        this.currentActor = currentActor;
        this.clock = clock;
        this.events = events;
    }

    @Override
    public boolean available() {
        return vectors.getIfAvailable() != null;
    }

    @Override
    @Transactional(timeout = 45)
    public KnowledgeDocument importDocument(ImportKnowledgeDocument command) {
        var actor = currentActor.actor();
        actor.requireAdmin();
        var store = vectors.getIfAvailable();
        if (store == null) {
            throw new IllegalStateException("knowledge indexing is not configured");
        }
        UUID id = UUID.randomUUID();
        String digest = digest(command);
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        int inserted = jdbc.sql("""
                        INSERT INTO ai.knowledge_documents
                            (id, tenant_id, kind, title, content, digest, imported_by, imported_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (tenant_id, digest) DO NOTHING
                        """)
                .params(
                        id,
                        actor.tenantId().value(),
                        command.kind().name(),
                        command.title(),
                        command.content(),
                        digest,
                        actor.subject(),
                        Timestamp.from(now))
                .update();
        if (inserted == 0) {
            UUID original = jdbc.sql("SELECT id FROM ai.knowledge_documents WHERE tenant_id = ? AND digest = ?")
                    .params(actor.tenantId().value(), digest)
                    .query(UUID.class)
                    .single();
            return get(actor.tenantId(), original);
        }
        List<Document> chunks = new ArrayList<>();
        String text = command.content();
        for (int start = 0; start < text.length(); ) {
            int end = Math.min(start + 2000, text.length());
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
                end--;
            }
            String part = text.substring(start, end);
            chunks.add(Document.builder()
                    .id(UUID.randomUUID().toString())
                    .text(part)
                    .metadata(Map.of(
                            "tenantId",
                            actor.tenantId().value().toString(),
                            "documentId",
                            id.toString(),
                            "kind",
                            command.kind().name(),
                            "title",
                            command.title(),
                            "chunkIndex",
                            chunks.size()))
                    .build());
            start = end;
        }
        store.add(chunks);
        events.publishEvent(
                new KnowledgeDocumentImported(UUID.randomUUID(), actor.tenantId(), id, actor.subject(), now));
        return get(actor.tenantId(), id);
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgeDocument get(TenantId tenantId, UUID id) {
        List<UUID> chunks =
                jdbc.sql("""
                        SELECT id FROM ai.knowledge_chunks WHERE tenant_id = ? AND document_id = ?
                        ORDER BY (metadata->>'chunkIndex')::INTEGER
                        """).params(tenantId.value(), id).query(UUID.class).list();
        return jdbc.sql("SELECT * FROM ai.knowledge_documents WHERE tenant_id = ? AND id = ?")
                .params(tenantId.value(), id)
                .query((rs, row) -> new KnowledgeDocument(
                        id,
                        tenantId,
                        KnowledgeKind.valueOf(rs.getString("kind")),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getString("imported_by"),
                        rs.getTimestamp("imported_at").toInstant(),
                        chunks))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("knowledge document not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgePassage passage(TenantId tenantId, UUID chunkId) {
        return jdbc.sql("""
                        SELECT c.id, c.document_id, c.content, c.metadata->>'chunkIndex' AS chunk_index, d.kind, d.title
                        FROM ai.knowledge_chunks c JOIN ai.knowledge_documents d
                            ON d.id = c.document_id AND d.tenant_id = c.tenant_id
                        WHERE c.tenant_id = ? AND c.id = ?
                        """)
                .params(tenantId.value(), chunkId)
                .query((rs, row) -> new KnowledgePassage(
                        chunkId,
                        rs.getObject("document_id", UUID.class),
                        KnowledgeKind.valueOf(rs.getString("kind")),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getInt("chunk_index")))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("knowledge passage not found"));
    }

    private static String digest(ImportKnowledgeDocument command) {
        try {
            String value =
                    command.kind().name() + ":" + command.title().length() + ":" + command.title() + command.content();
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
