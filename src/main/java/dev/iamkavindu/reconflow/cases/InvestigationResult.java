package dev.iamkavindu.reconflow.cases;

import java.util.List;
import java.util.Objects;

public record InvestigationResult(
        String summary,
        String likelyCause,
        List<InvestigationEvidence> evidence,
        ResolutionType suggestedResolution,
        double confidence,
        List<String> limitations,
        InvestigationJudgment judgment) {

    public InvestigationResult {
        Objects.requireNonNull(summary);
        Objects.requireNonNull(likelyCause);
        Objects.requireNonNull(judgment);
        evidence = List.copyOf(evidence);
        limitations = List.copyOf(limitations);
        if (summary.isBlank() || summary.length() > 4000 || likelyCause.isBlank() || likelyCause.length() > 1000
                || evidence.isEmpty() || evidence.size() > 30 || limitations.size() > 20
                || !Double.isFinite(confidence) || confidence < 0 || confidence > 1
                || limitations.stream().anyMatch(value -> value.isBlank() || value.length() > 1000)) {
            throw new IllegalArgumentException("investigation recommendations must be bounded and cite evidence");
        }
    }
}
