package dev.iamkavindu.reconflow.provider.internal.web;

import dev.iamkavindu.reconflow.provider.ProviderEventReceipt;
import dev.iamkavindu.reconflow.provider.ProviderSimulationCommands;
import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import dev.iamkavindu.reconflow.provider.SimulateProviderEventCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProviderSimulationController {
    private final ProviderSimulationCommands simulation;

    ProviderSimulationController(ProviderSimulationCommands simulation) {
        this.simulation = simulation;
    }

    @PostMapping("/api/provider-events/simulate")
    ResponseEntity<ProviderEventReceipt> simulate(@Valid @RequestBody Request request) {
        var receipt = simulation.simulate(new SimulateProviderEventCommand(
                request.providerEventId(),
                request.providerTransactionReference(),
                request.businessReference(),
                request.type(),
                request.amount(),
                request.currency(),
                request.status(),
                request.occurredAt()));
        return ResponseEntity.accepted().body(receipt);
    }

    record Request(
            @NotBlank @Size(max = 200) String providerEventId,
            @NotBlank @Size(max = 200) String providerTransactionReference,
            @NotBlank @Size(max = 100) String businessReference,
            @NotNull ProviderTransactionType type,
            @NotNull @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @NotNull Currency currency,
            @NotNull ProviderTransactionStatus status,
            @NotNull Instant occurredAt) {}
}
