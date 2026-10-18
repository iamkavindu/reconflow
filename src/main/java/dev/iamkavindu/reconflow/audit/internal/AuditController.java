package dev.iamkavindu.reconflow.audit.internal;

import dev.iamkavindu.reconflow.audit.AuditEntry;
import dev.iamkavindu.reconflow.audit.AuditQueries;
import dev.iamkavindu.reconflow.audit.AuditResourceType;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuditController {

    private final AuditQueries queries;
    private final CurrentActor currentActor;

    AuditController(AuditQueries queries, CurrentActor currentActor) {
        this.queries = queries;
        this.currentActor = currentActor;
    }

    @GetMapping("/api/audit/{type}/{id}")
    List<AuditEntry> history(@PathVariable AuditResourceType type, @PathVariable UUID id,
            @RequestParam(defaultValue = "9223372036854775807") long before,
            @RequestParam(defaultValue = "50") int limit) {
        return queries.forResource(currentActor.actor().tenantId(), type, id, before, limit);
    }
}
