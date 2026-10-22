package dev.iamkavindu.reconflow.ai.internal;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiModelConfigurationTest {

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-PT1S", "PT2M0.001S"})
    void rejectsUnboundedOrNonPositiveRequestTimeouts(String timeout) {
        var configuration = new AiModelConfiguration();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> configuration.investigationChatModel(
                        "contract-test", "fake-key", "http://127.0.0.1", Duration.parse(timeout), 8192))
                .withMessage("AI request timeout must be positive and at most two minutes");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-PT1S", "PT2M0.001S"})
    void rejectsUnboundedOrNonPositiveTypeSafeTimeouts(String timeout) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AiModelConfiguration()
                        .typeSafeClient("fake-key", "http://127.0.0.1", Duration.parse(timeout)))
                .withMessage("TypeSafe request timeout must be positive and at most two minutes");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 32769})
    void rejectsUnboundedOrNonPositiveCompletionBudgets(int budget) {
        var configuration = new AiModelConfiguration();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> configuration.investigationChatModel(
                        "contract-test", "fake-key", "http://127.0.0.1", Duration.ofSeconds(60), budget))
                .withMessage("AI completion token budget must be between 1 and 32768");
    }
}
