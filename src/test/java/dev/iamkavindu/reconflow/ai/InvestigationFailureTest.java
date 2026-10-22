package dev.iamkavindu.reconflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class InvestigationFailureTest {

    @Test
    void identifiesTimeoutThroughNestedCanceledCauseWithoutCopyingProviderMessages() {
        var progress = new InvestigationFailure.Progress();
        progress.stage(InvestigationFailure.Stage.GENERATION);
        var timeout = new InterruptedIOException("secret-provider-message");
        timeout.initCause(new IOException("Canceled"));
        var failure = new InvestigationFailure(
                new RuntimeException("secret-key", timeout), progress, Duration.ofSeconds(120));
        assertThat(failure.category()).isEqualTo("TIMEOUT");
        assertThat(failure.stage()).isEqualTo(InvestigationFailure.Stage.GENERATION);
        assertThat(failure.causeClass()).isEqualTo(IOException.class.getName());
        assertThat(failure.requestTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(failure.elapsedMs()).isGreaterThanOrEqualTo(0);
        assertThat(failure.totalElapsedMs()).isGreaterThanOrEqualTo(failure.elapsedMs());
        assertThat(failure.getMessage()).doesNotContain("secret");
    }

    @Test
    void distinguishesTransportValidationAndJudgmentFailures() {
        var progress = new InvestigationFailure.Progress();
        assertThat(new InvestigationFailure(new RuntimeException(new IOException()), progress, null).category())
                .isEqualTo("TRANSPORT");
        progress.stage(InvestigationFailure.Stage.VALIDATION);
        assertThat(new InvestigationFailure(new IllegalStateException(), progress, null).category())
                .isEqualTo("VALIDATION_FAILURE");
        progress.stage(InvestigationFailure.Stage.JUDGMENT);
        assertThat(new InvestigationFailure(new IllegalStateException(), progress, Duration.ofSeconds(60)).category())
                .isEqualTo("JUDGMENT_FAILURE");
    }
}
