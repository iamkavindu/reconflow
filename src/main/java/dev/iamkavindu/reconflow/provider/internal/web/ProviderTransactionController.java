package dev.iamkavindu.reconflow.provider.internal.web;

import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.provider.ProviderTransactionQueries;
import dev.iamkavindu.reconflow.provider.ProviderTransactionView;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProviderTransactionController {

    private final ProviderTransactionQueries queries;
    private final CurrentActor currentActor;

    ProviderTransactionController(ProviderTransactionQueries queries, CurrentActor currentActor) {
        this.queries = queries;
        this.currentActor = currentActor;
    }

    @GetMapping("/api/provider-transactions/{id}")
    ProviderTransactionView get(@PathVariable UUID id) {
        return queries.getProviderTransaction(currentActor.actor().tenantId(), new ProviderTransactionId(id));
    }
}
