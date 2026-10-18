package dev.iamkavindu.reconflow.provider.internal.application;

import dev.iamkavindu.reconflow.provider.ImportSettlementCommand;
import dev.iamkavindu.reconflow.provider.SettlementCommands;
import dev.iamkavindu.reconflow.provider.SettlementId;
import dev.iamkavindu.reconflow.provider.SettlementLine;
import dev.iamkavindu.reconflow.provider.SettlementView;
import dev.iamkavindu.reconflow.provider.events.SettlementImported;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderCode;
import dev.iamkavindu.reconflow.provider.internal.domain.ProviderReference;
import dev.iamkavindu.reconflow.provider.internal.persistence.SettlementRepository;
import dev.iamkavindu.reconflow.provider.internal.persistence.SqlSettlementQueries;
import dev.iamkavindu.reconflow.tenancy.Actor;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import dev.iamkavindu.reconflow.tenancy.OperationNotAuthorizedException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class SettlementApplicationService implements SettlementCommands {

    private final SettlementRepository repository;
    private final SqlSettlementQueries queries;
    private final JsonMapper json;
    private final Clock clock;
    private final CurrentActor currentActor;
    private final ApplicationEventPublisher events;

    SettlementApplicationService(
            SettlementRepository repository,
            SqlSettlementQueries queries,
            JsonMapper json,
            Clock clock,
            CurrentActor currentActor,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.queries = queries;
        this.json = json;
        this.clock = clock;
        this.currentActor = currentActor;
        this.events = events;
    }

    @Override
    @Transactional
    public SettlementView importSettlement(ImportSettlementCommand command) {
        Objects.requireNonNull(command.tenantId());
        var actor = currentActor.optionalActor();
        actor.ifPresent(value -> {
            value.requireAnalyst();
            if (!value.tenantId().equals(command.tenantId())) {
                throw new OperationNotAuthorizedException("settlement tenant must match the authenticated actor");
            }
        });
        String provider = new ProviderCode(command.provider()).value();
        String reference = new ProviderReference(command.providerSettlementReference()).value();
        int digits = Objects.requireNonNull(command.currency()).getDefaultFractionDigits();
        if (digits < 0
                || digits > 4
                || command.lines().isEmpty()
                || command.lines().size() > 1000) {
            throw new IllegalArgumentException("settlement requires a supported currency and 1 to 1000 source lines");
        }
        var lines = command.lines().stream()
                .map(line -> new SettlementLine(
                        line.providerTransactionReference(), money(line.grossAmount(), digits),
                        money(line.feeAmount(), digits), money(line.netAmount(), digits)))
                .sorted(Comparator.comparing(SettlementLine::providerTransactionReference))
                .toList();
        var references = new HashSet<String>();
        for (var line : lines) {
            if (!references.add(line.providerTransactionReference())) {
                throw new IllegalArgumentException("a provider transaction reference appears more than once");
            }
        }
        BigDecimal gross = money(command.grossAmount(), digits);
        BigDecimal fee = money(command.feeAmount(), digits);
        BigDecimal net = money(command.netAmount(), digits);
        BigDecimal totalGross =
                lines.stream().map(SettlementLine::grossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFee = lines.stream().map(SettlementLine::feeAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalNet = lines.stream().map(SettlementLine::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (gross.compareTo(totalGross) != 0 || fee.compareTo(totalFee) != 0 || net.compareTo(totalNet) != 0) {
            throw new IllegalArgumentException("reported settlement totals must agree with source lines");
        }
        Instant settledAt = Objects.requireNonNull(command.settledAt()).truncatedTo(ChronoUnit.MICROS);
        Instant recordedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        var id = new SettlementId(UUID.randomUUID());
        int inserted = repository.insertIfAbsent(
                id.value(),
                command.tenantId().value(),
                provider,
                reference,
                command.currency().getCurrencyCode(),
                gross,
                fee,
                net,
                settledAt,
                recordedAt,
                json.writeValueAsString(lines));
        var saved = queries.forSource(command.tenantId(), provider, reference);
        if (!saved.currency().equals(command.currency().getCurrencyCode())
                || !saved.settledAt().equals(settledAt)
                || saved.grossAmount().compareTo(gross) != 0
                || saved.feeAmount().compareTo(fee) != 0
                || saved.netAmount().compareTo(net) != 0
                || !saved.lines().equals(lines)) {
            throw new IllegalStateException("a different immutable settlement already uses this source identity");
        }
        if (inserted == 1) {
            events.publishEvent(new SettlementImported(
                    UUID.randomUUID(),
                    saved.tenantId(),
                    saved.settlementId(),
                    recordedAt,
                    actor.map(Actor::subject).orElse(null),
                    UUID.randomUUID()));
        }
        return saved;
    }

    private static BigDecimal money(BigDecimal value, int digits) {
        Objects.requireNonNull(value);
        if (value.precision() - value.scale() > 15 || value.stripTrailingZeros().scale() > digits) {
            throw new IllegalArgumentException("amount exceeds currency precision or settlement storage bounds");
        }
        return value.setScale(digits);
    }
}
