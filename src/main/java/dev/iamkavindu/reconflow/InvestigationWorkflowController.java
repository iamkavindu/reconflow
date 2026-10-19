package dev.iamkavindu.reconflow;

import dev.iamkavindu.reconflow.ai.InvestigationEngine;
import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.InvestigationCommands;
import dev.iamkavindu.reconflow.cases.InvestigationId;
import dev.iamkavindu.reconflow.cases.InvestigationQueries;
import dev.iamkavindu.reconflow.cases.InvestigationView;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class InvestigationWorkflowController {

    private final InvestigationCommands commands;
    private final InvestigationQueries queries;
    private final InvestigationEngine engine;
    private final CurrentActor currentActor;

    InvestigationWorkflowController(InvestigationCommands commands, InvestigationQueries queries,
            InvestigationEngine engine, CurrentActor currentActor) {
        this.commands = commands;
        this.queries = queries;
        this.engine = engine;
        this.currentActor = currentActor;
    }

    @PostMapping("/api/cases/{id}/investigations")
    ResponseEntity<InvestigationView> request(@PathVariable UUID id) {
        currentActor.actor().requireAnalyst();
        if (!engine.available()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI investigation is not configured.");
        }
        var result = commands.request(new CaseId(id));
        return ResponseEntity.accepted()
                .location(URI.create("/api/investigations/" + result.investigationId().value())).body(result);
    }

    @GetMapping("/api/investigations/{id}")
    InvestigationView get(@PathVariable UUID id) {
        return queries.get(currentActor.actor().tenantId(), new InvestigationId(id));
    }

    @GetMapping("/api/cases/{id}/investigations")
    List<InvestigationView> history(@PathVariable UUID id) {
        return queries.forCase(currentActor.actor().tenantId(), new CaseId(id));
    }
}
