package dev.iamkavindu.reconflow.cases;

public record InvestigationJudgment(boolean accepted, double groundedness, double relevance, int refinementAttempts) {

    public InvestigationJudgment {
        if (!Double.isFinite(groundedness)
                || !Double.isFinite(relevance)
                || groundedness < 0
                || groundedness > 1
                || relevance < 0
                || relevance > 1
                || refinementAttempts < 0
                || refinementAttempts > 1) {
            throw new IllegalArgumentException("judgment scores and refinement count must be bounded");
        }
    }
}
