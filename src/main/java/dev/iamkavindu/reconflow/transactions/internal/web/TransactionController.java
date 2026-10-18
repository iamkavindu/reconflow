package dev.iamkavindu.reconflow.transactions.internal.web;

import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import dev.iamkavindu.reconflow.transactions.RecordTransactionCommand;
import dev.iamkavindu.reconflow.transactions.TransactionCommands;
import dev.iamkavindu.reconflow.transactions.TransactionId;
import dev.iamkavindu.reconflow.transactions.TransactionQueries;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import dev.iamkavindu.reconflow.transactions.TransactionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
class TransactionController {

    private final TransactionCommands commands;
    private final TransactionQueries queries;
    private final CurrentActor currentActor;

    TransactionController(TransactionCommands commands, TransactionQueries queries, CurrentActor currentActor) {
        this.commands = commands;
        this.queries = queries;
        this.currentActor = currentActor;
    }

    @PostMapping
    ResponseEntity<TransactionView> record(@Valid @RequestBody RecordRequest request) {
        currentActor.actor().requireAnalyst();
        var command = new RecordTransactionCommand(
                request.businessReference(), request.type(), request.amount(),
                Currency.getInstance(request.currency()), request.occurredAt());
        var result = commands.recordTransaction(command);
        return ResponseEntity.created(URI.create("/api/transactions/" + result.transactionId().value())).body(result);
    }

    @GetMapping("/{id}")
    TransactionView get(@PathVariable UUID id) {
        return queries.getTransaction(currentActor.actor().tenantId(), new TransactionId(id));
    }

    record RecordRequest(
            @NotBlank @Size(max = 100) String businessReference,
            @NotNull TransactionType type,
            @NotNull @Positive BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotNull Instant occurredAt) {}
}
