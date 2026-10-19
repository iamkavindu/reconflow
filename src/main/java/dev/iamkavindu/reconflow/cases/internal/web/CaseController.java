package dev.iamkavindu.reconflow.cases.internal.web;

import dev.iamkavindu.reconflow.cases.CaseCommands;
import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.CaseQueries;
import dev.iamkavindu.reconflow.cases.CaseView;
import dev.iamkavindu.reconflow.cases.ResolutionType;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class CaseController {

    private final CaseCommands commands;
    private final CaseQueries queries;
    private final CurrentActor currentActor;

    CaseController(CaseCommands commands, CaseQueries queries, CurrentActor currentActor) {
        this.commands = commands;
        this.queries = queries;
        this.currentActor = currentActor;
    }

    @GetMapping("/cases/{id}")
    CaseView get(@PathVariable UUID id) {
        return queries.getCase(currentActor.actor().tenantId(), new CaseId(id));
    }

    @GetMapping("/reconciliations/{id}/case")
    CaseView forReconciliation(@PathVariable UUID id) {
        return queries.getCaseForReconciliation(currentActor.actor().tenantId(), new ReconciliationId(id));
    }

    @PostMapping("/cases/{id}/review")
    CaseView startReview(@PathVariable UUID id) {
        return commands.startReview(new CaseId(id));
    }

    @PostMapping("/cases/{id}/notes")
    CaseView addNote(@PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        return commands.addNote(new CaseId(id), request.text());
    }

    @PostMapping("/cases/{id}/resolution")
    CaseView resolve(@PathVariable UUID id, @Valid @RequestBody ResolutionRequest request) {
        return commands.resolveCase(new CaseId(id), request.type(), request.reason(), request.evidenceReferences());
    }

    record NoteRequest(@NotBlank @Size(max = 4000) String text) {}

    record ResolutionRequest(
            @NotNull ResolutionType type,
            @NotBlank @Size(max = 4000) String reason,
            @NotNull @Size(max = 100) List<@NotBlank @Size(max = 500) String> evidenceReferences) {}
}
