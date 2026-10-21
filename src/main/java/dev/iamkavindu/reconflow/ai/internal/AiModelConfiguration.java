package dev.iamkavindu.reconflow.ai.internal;

import java.time.Duration;
import org.springaicommunity.typesafe.RetryPolicy;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.judge.JevJudge;
import org.springaicommunity.typesafe.question.Noul;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "reconflow.ai.enabled", havingValue = "true")
class AiModelConfiguration {

    @Bean
    ChatModel investigationChatModel(
            @Value("${reconflow.ai.model}") String model,
            @Value("${reconflow.ai.api-key}") String apiKey,
            @Value("${reconflow.ai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${reconflow.ai.request-timeout:PT60S}") Duration requestTimeout,
            @Value("${reconflow.ai.max-completion-tokens:8192}") int maxCompletionTokens) {
        if (model.isBlank() || model.length() > 200 || apiKey.isBlank()) {
            throw new IllegalArgumentException("AI execution requires a bounded model name and an API key");
        }
        if (requestTimeout.compareTo(Duration.ZERO) <= 0 || requestTimeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException("AI request timeout must be positive and at most two minutes");
        }
        if (maxCompletionTokens < 1 || maxCompletionTokens > 32768) {
            throw new IllegalArgumentException("AI completion token budget must be between 1 and 32768");
        }
        var options = OpenAiChatOptions.builder()
                .model(model)
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .timeout(requestTimeout)
                .maxRetries(0)
                .maxCompletionTokens(maxCompletionTokens)
                .build();
        return OpenAiChatModel.builder().options(options).build();
    }

    @Bean
    TypeSafeClient typeSafeClient(
            @Value("${reconflow.ai.typesafe-api-key}") String apiKey,
            @Value("${reconflow.ai.typesafe-base-url:https://api.typesafe.ai}") String baseUrl) {
        return TypeSafeClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(10))
                .retryPolicy(RetryPolicy.noRetry())
                .build();
    }

    @Bean
    JevJudge investigationJudge(TypeSafeClient client) {
        return JevJudge.builder(client)
                .noul(
                        "groundedness",
                        Noul.of("Are all factual claims in assistant_answer supported by supporting_context, "
                                + "with valid evidence references and no invented provider fee contract or policy? "
                                + "Each evidence explanation must be supported by the specific record it cites. "
                                + "Distinguish reported settlement fees and explicitly labeled arithmetic inferences "
                                + "from claims that a contractual fee or acceptance policy has been verified."),
                        0.8)
                .noul(
                        "relevance",
                        Noul.of(
                                "Does assistant_answer explain the reconciliation exception in user_question, "
                                        + "distinguish reported financial evidence from inference, and state material limitations?"),
                        0.8)
                .noul(
                        "advisory_only",
                        Noul.of("Is assistant_answer strictly an advisory recommendation for human review, "
                                + "without claiming to change financial sources, move money, issue refunds, or resolve the case? "
                                + "The JSON field suggestedResolution proposes a future human decision; an enum value "
                                + "alone is not a claim that a case was resolved or a review completed. A null value "
                                + "with a recommendation for further human review is also advisory. Reject claims "
                                + "that an action has already been applied or will occur without human authorization."),
                        0.9)
                .failOnError(true)
                .failOnInconclusive(true)
                .build();
    }
}
