package dev.iamkavindu.reconflow.cases.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.CaseNote;
import dev.iamkavindu.reconflow.cases.CaseStatus;
import dev.iamkavindu.reconflow.cases.Resolution;
import dev.iamkavindu.reconflow.cases.ResolutionType;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationId;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CaseTest {

    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");

    @Test
    void preservesNotesAndResolutionAcrossTheAllowedLifecycle() {
        var open = Case.open(
                new CaseId(UUID.randomUUID()),
                new TenantId(UUID.randomUUID()),
                new ReconciliationId(UUID.randomUUID()),
                NOW);
        var note = new CaseNote(UUID.randomUUID(), "Provider fee evidence reviewed", "analyst-1", NOW.plusSeconds(1));
        var resolution = new Resolution(
                ResolutionType.ACCEPTED_FEE_VARIANCE,
                "Confirmed against provider documentation",
                "analyst-1",
                NOW.plusSeconds(2),
                List.of("provider-fees-policy"));

        var resolved = open.startReview(NOW.plusSeconds(1)).addNote(note).resolve(resolution);

        assertThat(open.status()).isEqualTo(CaseStatus.OPEN);
        assertThat(resolved.status()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(resolved.notes()).containsExactly(note);
        assertThat(resolved.resolution()).isEqualTo(resolution);
        assertThatThrownBy(() -> resolved.startReview(NOW.plusSeconds(3))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> resolved.addNote(note)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> resolved.resolve(resolution)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidResolutionStateAndHistoricalEdits() {
        var id = new CaseId(UUID.randomUUID());
        var tenant = new TenantId(UUID.randomUUID());
        var reconciliation = new ReconciliationId(UUID.randomUUID());
        var open = Case.open(id, tenant, reconciliation, NOW);

        assertThatThrownBy(() -> new Case(
                        id, tenant, reconciliation, CaseStatus.RESOLVED, NOW, NOW, List.of(), null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> open.startReview(NOW.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Resolution(ResolutionType.MATCH_CONFIRMED, " ", "analyst", NOW, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
