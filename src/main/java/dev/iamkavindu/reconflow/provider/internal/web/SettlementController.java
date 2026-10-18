package dev.iamkavindu.reconflow.provider.internal.web;

import dev.iamkavindu.reconflow.provider.ImportSettlementCommand;
import dev.iamkavindu.reconflow.provider.ProviderTransactionId;
import dev.iamkavindu.reconflow.provider.SettlementCommands;
import dev.iamkavindu.reconflow.provider.SettlementId;
import dev.iamkavindu.reconflow.provider.SettlementLine;
import dev.iamkavindu.reconflow.provider.SettlementQueries;
import dev.iamkavindu.reconflow.provider.SettlementView;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SettlementController {

    private final SettlementCommands commands;
    private final SettlementQueries queries;
    private final CurrentActor currentActor;

    SettlementController(SettlementCommands commands, SettlementQueries queries, CurrentActor currentActor) {
        this.commands = commands;
        this.queries = queries;
        this.currentActor = currentActor;
    }

    @PostMapping("/api/settlements")
    SettlementView importSettlement(@Valid @RequestBody ImportRequest request) {
        var actor = currentActor.actor();
        actor.requireAnalyst();
        var command = new ImportSettlementCommand(
                actor.tenantId(),
                request.provider(),
                request.providerSettlementReference(),
                Currency.getInstance(request.currency()),
                request.grossAmount(),
                request.feeAmount(),
                request.netAmount(),
                request.settledAt(),
                request.lines());
        return commands.importSettlement(command);
    }

    @GetMapping("/api/settlements/{id}")
    SettlementView get(@PathVariable UUID id) {
        return queries.getSettlement(currentActor.actor().tenantId(), new SettlementId(id));
    }

    @GetMapping("/api/provider-transactions/{id}/settlements")
    List<SettlementView> forTransaction(@PathVariable UUID id) {
        return queries.forProviderTransaction(currentActor.actor().tenantId(), new ProviderTransactionId(id));
    }

    record ImportRequest(
            @NotBlank String provider,
            @NotBlank String providerSettlementReference,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotNull BigDecimal grossAmount,
            @NotNull BigDecimal feeAmount,
            @NotNull BigDecimal netAmount,
            @NotNull Instant settledAt,
            @NotEmpty @Size(max = 1000) List<@NotNull SettlementLine> lines) {}
}
