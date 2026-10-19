package dev.iamkavindu.reconflow.cases;

import java.util.Objects;

public record InvestigationEvidence(String reference, String explanation) {

    public InvestigationEvidence {
        Objects.requireNonNull(reference);
        Objects.requireNonNull(explanation);
        if (reference.isBlank() || reference.length() > 200 || explanation.isBlank() || explanation.length() > 1000) {
            throw new IllegalArgumentException("evidence requires a bounded reference and explanation");
        }
    }
}
