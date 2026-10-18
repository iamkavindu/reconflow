package dev.iamkavindu.reconflow.reconciliation;

import java.util.Objects;

public record RuleEvaluation(String rule, boolean satisfied, String expected, String actual) {

    public RuleEvaluation {
        Objects.requireNonNull(rule, "rule must not be null");
        Objects.requireNonNull(expected, "expected must not be null");
        Objects.requireNonNull(actual, "actual must not be null");
    }
}
