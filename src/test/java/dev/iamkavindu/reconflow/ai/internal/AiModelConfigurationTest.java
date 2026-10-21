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
                        "contract-test", "fake-key", "http://127.0.0.1", Duration.parse(timeout)))
                .withMessage("AI request timeout must be positive and at most two minutes");
    }
}
