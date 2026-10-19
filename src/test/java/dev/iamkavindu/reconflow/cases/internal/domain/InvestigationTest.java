package dev.iamkavindu.reconflow.cases.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.iamkavindu.reconflow.cases.CaseId;
import dev.iamkavindu.reconflow.cases.InvestigationEvidence;
import dev.iamkavindu.reconflow.cases.InvestigationId;
import dev.iamkavindu.reconflow.cases.InvestigationJudgment;
import dev.iamkavindu.reconflow.cases.InvestigationResult;
import dev.iamkavindu.reconflow.cases.ResolutionType;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvestigationTest {

    @Test
    void requiresRunningStateAndAnAcceptedJudgmentBeforeCompletion() {
        var requested = Investigation.request(
                new InvestigationId(UUID.randomUUID()),
                new TenantId(UUID.randomUUID()),
                new CaseId(UUID.randomUUID()),
                "analyst",
                Instant.EPOCH);
        var result = result(new InvestigationJudgment(true, 0.9, 0.9, 0));
        assertThatThrownBy(() -> requested.complete(result, "model", "v1", Instant.EPOCH.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        var running = requested.start(Instant.EPOCH.plusSeconds(1));
        assertThatThrownBy(() -> running.complete(
                        result(new InvestigationJudgment(false, 0.2, 0.3, 1)),
                        "model",
                        "v1",
                        Instant.EPOCH.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        var completed = running.complete(result, "model", "v1", Instant.EPOCH.plusSeconds(2));
        assertThat(completed.fail("late failure", Instant.EPOCH.plusSeconds(3))).isSameAs(completed);
    }

    @Test
    void rejectsInvalidScoresAndFreezesEvidenceAndLimitations() {
        assertThatThrownBy(() -> new InvestigationJudgment(true, Double.NaN, 0.5, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvestigationJudgment(true, 0.9, 0.9, 2))
                .isInstanceOf(IllegalArgumentException.class);
        var evidence = new ArrayList<>(List.of(new InvestigationEvidence("transaction:demo", "Recorded source")));
        var result = new InvestigationResult(
                "Summary",
                "Cause",
                evidence,
                null,
                0.5,
                List.of("Incomplete policy"),
                new InvestigationJudgment(true, 0.9, 0.9, 0));
        evidence.clear();
        assertThat(result.evidence()).hasSize(1);
        assertThatThrownBy(() -> result.evidence().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private InvestigationResult result(InvestigationJudgment judgment) {
        return new InvestigationResult(
                "Summary",
                "Cause",
                List.of(new InvestigationEvidence("case:demo", "Case source")),
                ResolutionType.MANUAL_REVIEW_COMPLETED,
                0.5,
                List.of("Policy needs human confirmation"),
                judgment);
    }
}
