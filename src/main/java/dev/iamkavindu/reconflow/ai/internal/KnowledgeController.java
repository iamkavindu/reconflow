package dev.iamkavindu.reconflow.ai.internal;

import dev.iamkavindu.reconflow.ai.ImportKnowledgeDocument;
import dev.iamkavindu.reconflow.ai.KnowledgeDocument;
import dev.iamkavindu.reconflow.ai.KnowledgeDocuments;
import dev.iamkavindu.reconflow.ai.KnowledgePassage;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class KnowledgeController {

    private final KnowledgeDocuments documents;
    private final CurrentActor actor;

    KnowledgeController(KnowledgeDocuments documents, CurrentActor actor) {
        this.documents = documents;
        this.actor = actor;
    }

    @PostMapping("/api/knowledge/documents")
    ResponseEntity<KnowledgeDocument> importDocument(@RequestBody ImportKnowledgeDocument command) {
        actor.actor().requireAdmin();
        if (!documents.available()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Knowledge indexing is not configured.");
        }
        var saved = documents.importDocument(command);
        return ResponseEntity.ok().location(URI.create("/api/knowledge/documents/" + saved.id())).body(saved);
    }

    @GetMapping("/api/knowledge/documents/{id}")
    KnowledgeDocument get(@PathVariable UUID id) {
        return documents.get(actor.actor().tenantId(), id);
    }

    @GetMapping("/api/knowledge/passages/{id}")
    KnowledgePassage passage(@PathVariable UUID id) {
        return documents.passage(actor.actor().tenantId(), id);
    }
}
