package dev.iamkavindu.reconflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.iamkavindu.reconflow.ai.InvestigationEngine;
import dev.iamkavindu.reconflow.ai.KnowledgeEvidence;
import dev.iamkavindu.reconflow.ai.KnowledgeKind;
import dev.iamkavindu.reconflow.ai.KnowledgeSearch;
import dev.iamkavindu.reconflow.cases.CaseQueries;
import dev.iamkavindu.reconflow.cases.CaseView;
import dev.iamkavindu.reconflow.cases.InvestigationCommands;
import dev.iamkavindu.reconflow.cases.InvestigationQueries;
import dev.iamkavindu.reconflow.cases.InvestigationStatus;
import dev.iamkavindu.reconflow.cases.InvestigationView;
import dev.iamkavindu.reconflow.provider.ImportSettlementCommand;
import dev.iamkavindu.reconflow.provider.IngestProviderEventCommand;
import dev.iamkavindu.reconflow.provider.ProviderTransactionCommands;
import dev.iamkavindu.reconflow.provider.ProviderTransactionStatus;
import dev.iamkavindu.reconflow.provider.ProviderTransactionType;
import dev.iamkavindu.reconflow.provider.SettlementCommands;
import dev.iamkavindu.reconflow.provider.SettlementLine;
import dev.iamkavindu.reconflow.reconciliation.ReconciliationQueries;
import dev.iamkavindu.reconflow.tenancy.Actor;
import dev.iamkavindu.reconflow.tenancy.CurrentActor;
import dev.iamkavindu.reconflow.tenancy.TenantId;
import dev.iamkavindu.reconflow.tenancy.UserRole;
import dev.iamkavindu.reconflow.transactions.RecordTransactionCommand;
import dev.iamkavindu.reconflow.transactions.TransactionCommands;
import dev.iamkavindu.reconflow.transactions.TransactionType;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import({TestcontainersConfiguration.class, AiInvestigationIntegrationTest.ActorConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        properties = {
            "reconflow.kafka.enabled=false",
            "reconflow.security.enabled=false",
            "reconflow.ai.enabled=true",
            "reconflow.ai.model=contract-test",
            "reconflow.ai.request-timeout=PT45S",
            "reconflow.ai.max-completion-tokens=4096",
            "reconflow.ai.api-key=fake-model-key",
            "reconflow.ai.typesafe-api-key=fake-judge-key",
            "reconflow.reconciliation.scheduling-enabled=false"
        })
class AiInvestigationIntegrationTest {

    private static final TenantId TENANT = new TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final FakeModels MODELS = new FakeModels();

    @DynamicPropertySource
    static void endpoints(DynamicPropertyRegistry properties) {
        properties.add("reconflow.ai.base-url", () -> MODELS.url() + "/v1");
        properties.add("reconflow.ai.typesafe-base-url", MODELS::url);
    }

    @AfterAll
    static void stop() {
        MODELS.server.stop(0);
    }

    @Autowired
    InvestigationCommands commands;

    @Autowired
    InvestigationQueries investigations;

    @Autowired
    TransactionCommands transactions;

    @Autowired
    ProviderTransactionCommands provider;

    @Autowired
    SettlementCommands settlements;

    @Autowired
    ReconciliationQueries reconciliations;

    @Autowired
    CaseQueries cases;

    @Autowired
    ApplicationContext context;

    @Autowired
    InvestigationEngine engine;

    @MockitoBean
    KnowledgeSearch knowledge;

    @BeforeEach
    void resetModels() {
        when(knowledge.search(any(), any(), any())).thenReturn(List.of());
        MODELS.mode = Mode.SUCCESS;
        MODELS.chatCalls.set(0);
        MODELS.judgeCalls.set(0);
        MODELS.toolOutputs.clear();
        MODELS.chatRequests.clear();
        MODELS.judgeRequests.clear();
    }

    @Test
    void returnsAValidatedRecommendationFromTheConfiguredHttpAdapters() {
        var original = exception();
        var result = engine.investigate(TENANT, original.caseId());
        assertThat(result.judgment().accepted()).isTrue();
        assertThat(result.evidence().getFirst().reference()).isEqualTo(MODELS.reference);
        assertThat(engine.promptVersion()).isEqualTo("reconflow-investigation-v3");
        String instructions = MODELS.chatRequests
                .getFirst()
                .get("messages")
                .get(0)
                .get("content")
                .asString();
        assertThat(instructions)
                .contains("specific record it cites", "search both policy and provider documentation")
                .contains("use null, not MANUAL_REVIEW_COMPLETED");
        assertThat(MODELS.judgeRequests.getFirst().toString())
                .contains("specific record it cites", "an enum value alone is not a claim")
                .contains("supporting_context", MODELS.reference);
        verify(knowledge).search(eq(TENANT), eq(KnowledgeKind.POLICY), any());
        verify(knowledge).search(eq(TENANT), eq(KnowledgeKind.PROVIDER_DOCUMENTATION), any());
    }

    private void supplyDocumentation() {
        when(knowledge.search(eq(TENANT), eq(KnowledgeKind.POLICY), any()))
                .thenReturn(List.of(new KnowledgeEvidence(
                        "document-chunk:policy-fixture", "Fee review", "Reported fees require human review.",
                        "INCLUDED", 0.95)));
    }

    @Test
    void refinesMissingDocumentationBeforeJudgmentAndCompletesWithAnExactCitation() {
        supplyDocumentation();
        MODELS.mode = Mode.DOCUMENT_REFINE;
        var original = exception();
        var completed = execute(original);
        assertThat(completed.status()).isEqualTo(InvestigationStatus.COMPLETED);
        assertThat(completed.result().judgment().refinementAttempts()).isEqualTo(1);
        assertThat(completed.result().evidence()).anyMatch(value -> value.reference().equals("document-chunk:policy-fixture"));
        assertThat(MODELS.chatCalls).hasValue(2);
        assertThat(MODELS.judgeCalls).hasValue(1);
        assertThat(MODELS.chatRequests.getFirst().toString()).contains("document-chunk:policy-fixture");
        assertThat(MODELS.chatRequests.getLast().toString()).contains("Relevant documentation was supplied");
        assertThat(cases.getCase(TENANT, original.caseId())).isEqualTo(original);
    }

    @Test
    void retainsFailureWhenSuppliedDocumentationRemainsUncited() {
        supplyDocumentation();
        var original = exception();
        MODELS.mode = Mode.REVIEW_ONLY;
        assertThat(execute(original).status()).isEqualTo(InvestigationStatus.FAILED);
        assertThat(MODELS.chatCalls).hasValue(2);
        assertThat(MODELS.judgeCalls).hasValue(0);
        assertThat(cases.getCase(TENANT, original.caseId())).isEqualTo(original);
    }

    @Test
    void rejectsUnknownDocumentationEvenWithTheExpectedPrefix() {
        supplyDocumentation();
        MODELS.mode = Mode.DOCUMENT_UNKNOWN;
        var original = exception();
        assertThat(execute(original).status()).isEqualTo(InvestigationStatus.FAILED);
        assertThat(MODELS.chatCalls).hasValue(2);
        assertThat(MODELS.judgeCalls).hasValue(0);
    }

    @Test
    void citationAndJudgmentFailuresShareOneRefinementBudget() {
        supplyDocumentation();
        MODELS.mode = Mode.DOCUMENT_JUDGE_REJECT;
        var original = exception();
        assertThat(execute(original).status()).isEqualTo(InvestigationStatus.FAILED);
        assertThat(MODELS.chatCalls).hasValue(2);
        assertThat(MODELS.judgeCalls).hasValue(1);
    }

    @Test
    void acceptsFurtherHumanReviewWithoutInventingAFinalResolution() {
        MODELS.mode = Mode.REVIEW_ONLY;
        var original = exception();
        var completed = execute(original);
        assertThat(completed.status()).isEqualTo(InvestigationStatus.COMPLETED);
        assertThat(completed.result().suggestedResolution()).isNull();
        assertThat(completed.result().summary()).contains("case remains unchanged");
        assertThat(completed.result().evidence().getFirst().reference()).isEqualTo(MODELS.reference);
        assertThat(MODELS.judgeCalls).hasValue(1);
        assertThat(cases.getCase(TENANT, original.caseId())).isEqualTo(original);
    }

    @Test
    void keepsFinancialEventExecutionSeparateFromModelCalls() {
        assertThat(context.getBean("applicationTaskExecutor")).isNotSameAs(context.getBean("investigationExecutor"));
    }

    @Test
    void acceptsAModelResponseBeyondTheFormerFifteenSecondTimeout() {
        MODELS.mode = Mode.SLOW_RESPONSE;
        var original = exception();
        var result = engine.investigate(TENANT, original.caseId());
        assertThat(result.judgment().accepted()).isTrue();
    }

    @Test
    void executesReadOnlyToolsAndOneJudgmentRefinementWithoutChangingFinancialOrCaseState() {
        MODELS.mode = Mode.REFINE;
        var original = exception();
        var determination = reconciliations.getReconciliationById(TENANT, original.reconciliationId());
        var completed = execute(original);
        assertThat(completed.status()).isEqualTo(InvestigationStatus.COMPLETED);
        assertThat(completed.result().judgment().refinementAttempts()).isEqualTo(1);
        assertThat(completed.result().judgment().accepted()).isTrue();
        assertThat(completed.result().evidence().getFirst().reference()).isEqualTo(MODELS.reference);
        assertThat(completed.result().limitations()).anyMatch(value -> value.contains("No screened policy"));
        assertThat(MODELS.chatCalls).hasValue(3);
        assertThat(MODELS.judgeCalls).hasValue(2);
        assertThat(MODELS.toolOutputs).anyMatch(value -> value.contains(MODELS.reference));
        assertThat(MODELS.chatRequests.getLast().toString())
                .contains("specific cited record", "Use null for suggestedResolution");
        assertThat(cases.getCase(TENANT, original.caseId())).isEqualTo(original);
        assertThat(reconciliations.getReconciliationById(TENANT, original.reconciliationId()))
                .isEqualTo(determination);
    }

    @Test
    void rejectsFabricatedEvidenceAfterOneBoundedRefinement() {
        MODELS.mode = Mode.FABRICATED;
        var original = exception();
        var completed = execute(original);
        assertThat(completed.status()).isEqualTo(InvestigationStatus.FAILED);
        assertThat(completed.result()).isNull();
        assertThat(MODELS.chatCalls).hasValue(2);
        assertThat(MODELS.judgeCalls).hasValue(0);
        assertThat(cases.getCase(TENANT, original.caseId())).isEqualTo(original);
    }

    @Test
    void retainsTruncationAsAFailedAttemptWithoutRefiningOrJudgingIncompleteOutput() {
        MODELS.mode = Mode.TRUNCATED;
        var original = exception();
        var completed = execute(original);
        assertThat(completed.status()).isEqualTo(InvestigationStatus.FAILED);
        assertThat(completed.result()).isNull();
        assertThat(MODELS.chatCalls).hasValue(1);
        assertThat(MODELS.judgeCalls).hasValue(0);
        assertThat(cases.getCase(TENANT, original.caseId())).isEqualTo(original);
    }

    @Test
    void neverExposesARequestedCaseWriteTool() {
        MODELS.mode = Mode.ILLEGAL_WRITE;
        var original = exception();
        var completed = execute(original);
        assertThat(completed.status()).isEqualTo(InvestigationStatus.FAILED);
        assertThat(MODELS.chatCalls.get()).isBetween(1, 8);
        assertThat(MODELS.judgeCalls).hasValue(0);
        assertThat(cases.getCase(TENANT, original.caseId())).isEqualTo(original);
    }

    @Test
    void rejectedRecommendationsCannotRefineIndefinitely() {
        MODELS.mode = Mode.REJECT;
        var original = exception();
        assertThat(execute(original).status()).isEqualTo(InvestigationStatus.FAILED);
        assertThat(MODELS.chatCalls).hasValue(2);
        assertThat(MODELS.judgeCalls).hasValue(2);
    }

    @Test
    void judgeOutagesFailClosedWithoutPersistingProviderResponseDetails(CapturedOutput output) {
        MODELS.mode = Mode.OUTAGE;
        var original = exception();
        var completed = execute(original);
        assertThat(completed.status()).isEqualTo(InvestigationStatus.FAILED);
        assertThat(completed.result()).isNull();
        assertThat(completed.failureReason()).doesNotContain("secret-material", "fake-judge-key");
        assertThat(MODELS.judgeCalls).hasValue(1);
        assertThat(cases.getCase(TENANT, original.caseId())).isEqualTo(original);
        assertThat(output.getAll()).contains("workflow_stage=JUDGMENT", "error_category=", "cause_class=", "elapsed_ms=");
        assertThat(output.getAll()).doesNotContain("secret-material", "fake-judge-key");
    }

    private InvestigationView execute(CaseView investigationCase) {
        var requested = commands.request(investigationCase.caseId());
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(investigations
                                .get(TENANT, requested.investigationId())
                                .status())
                        .isIn(InvestigationStatus.COMPLETED, InvestigationStatus.FAILED));
        return investigations.get(TENANT, requested.investigationId());
    }

    private CaseView exception() {
        String reference = "AI-" + UUID.randomUUID();
        Instant now = Instant.now();
        var line = new SettlementLine(
                "pi-" + reference, new BigDecimal("100.00"), new BigDecimal("3.20"), new BigDecimal("96.80"));
        var settlement = settlements.importSettlement(new ImportSettlementCommand(
                TENANT,
                "stripe",
                "set-" + reference,
                Currency.getInstance("USD"),
                line.grossAmount(),
                line.feeAmount(),
                line.netAmount(),
                now,
                List.of(line)));
        MODELS.reference = "settlement:" + settlement.settlementId().value();
        var transaction = transactions.recordTransaction(new RecordTransactionCommand(
                reference, TransactionType.PAYMENT, new BigDecimal("100.00"), Currency.getInstance("USD"), now));
        provider.ingestProviderEvent(new IngestProviderEventCommand(
                TENANT,
                "evt-" + reference,
                "stripe",
                "pi-" + reference,
                reference,
                ProviderTransactionType.PAYMENT,
                new BigDecimal("96.80"),
                Currency.getInstance("USD"),
                ProviderTransactionStatus.SUCCEEDED,
                now,
                UUID.randomUUID(),
                UUID.randomUUID()));
        List<CaseView> found = new ArrayList<>();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var reconciliation = reconciliations.getReconciliation(TENANT, transaction.transactionId());
            found.add(cases.getCaseForReconciliation(TENANT, reconciliation.reconciliationId()));
        });
        return found.getLast();
    }

    enum Mode {
        SUCCESS,
        DOCUMENT_REFINE,
        DOCUMENT_UNKNOWN,
        DOCUMENT_JUDGE_REJECT,
        REVIEW_ONLY,
        SLOW_RESPONSE,
        TRUNCATED,
        REFINE,
        FABRICATED,
        ILLEGAL_WRITE,
        REJECT,
        OUTAGE
    }

    static class FakeModels {

        final JsonMapper json = JsonMapper.builder().build();
        final HttpServer server;
        final AtomicInteger chatCalls = new AtomicInteger();
        final AtomicInteger judgeCalls = new AtomicInteger();
        final List<String> toolOutputs = new CopyOnWriteArrayList<>();
        final List<JsonNode> chatRequests = new CopyOnWriteArrayList<>();
        final List<JsonNode> judgeRequests = new CopyOnWriteArrayList<>();
        volatile Mode mode = Mode.SUCCESS;
        volatile String reference;

        FakeModels() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/", this::handle);
                server.start();
            } catch (IOException error) {
                throw new ExceptionInInitializerError(error);
            }
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void handle(HttpExchange exchange) throws IOException {
            JsonNode input = json.readTree(exchange.getRequestBody());
            if (input.has("messages")) {
                chatRequests.add(input);
                if (!"45".equals(exchange.getRequestHeaders().getFirst("X-Stainless-Timeout"))) {
                    respond(exchange, 400, "{\"error\":\"Configured request timeout was not propagated\"}");
                    return;
                }
                if (input.has("max_tokens")
                        || !input.has("max_completion_tokens")
                        || input.get("max_completion_tokens").asInt() != 4096) {
                    respond(exchange, 400, "{\"error\":\"Use a bounded max_completion_tokens budget\"}");
                    return;
                }
                for (var message : input.get("messages")) {
                    if ("tool".equals(message.get("role").asString())) {
                        toolOutputs.add(message.get("content").asString());
                    }
                }
                int call = chatCalls.incrementAndGet();
                if (mode == Mode.TRUNCATED) {
                    respond(exchange, 200, chat("", null, "length"));
                    return;
                }
                if (mode == Mode.SLOW_RESPONSE && call == 1) {
                    try {
                        Thread.sleep(Duration.ofSeconds(16));
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        respond(exchange, 503, "{\"error\":\"Fixture interrupted\"}");
                        return;
                    }
                }
                if (mode == Mode.ILLEGAL_WRITE) {
                    respond(exchange, 200, chat(null, "resolveCase"));
                } else if ((mode == Mode.SUCCESS || mode == Mode.REFINE) && call == 1) {
                    respond(exchange, 200, chat(null, "getSettlement"));
                } else {
                    String citation = mode == Mode.FABRICATED ? "settlement:another-tenants-record" : reference;
                    Map<String, Object> recommendation = new java.util.LinkedHashMap<>(Map.of(
                            "summary",
                            "The reported fee explains the gross/net difference and needs human review.",
                            "likelyCause",
                            "Provider-reported fee",
                            "evidence",
                            List.of(Map.of(
                                    "reference", citation, "explanation", "Reported gross 100, fee 3.20, net 96.80")),
                            "suggestedResolution",
                            "ACCEPTED_FEE_VARIANCE",
                            "confidence",
                            0.8,
                            "limitations",
                            List.of("The contractual fee policy is unavailable.")));
                    if (mode == Mode.REVIEW_ONLY) {
                        recommendation.put("suggestedResolution", null);
                        recommendation.put(
                                "summary",
                                "The settlement reports gross 100, fee 3.20, net 96.80. "
                                        + "A human should verify fee terms; the case remains unchanged.");
                    }
                    if (mode == Mode.DOCUMENT_UNKNOWN) {
                        recommendation.put("evidence", List.of(Map.of(
                                "reference", "document-chunk:unavailable", "explanation", "Invented policy")));
                    } else if ((mode == Mode.DOCUMENT_REFINE || mode == Mode.DOCUMENT_JUDGE_REJECT) && call > 1) {
                        recommendation.put("evidence", List.of(Map.of(
                                "reference", "document-chunk:policy-fixture", "explanation", "Reported fees require human review.")));
                        recommendation.put("suggestedResolution", null);
                    }
                    String draft = json.writeValueAsString(recommendation);
                    respond(exchange, 200, chat(draft, null));
                }
            } else {
                judgeRequests.add(input);
                int call = judgeCalls.incrementAndGet();
                if (mode == Mode.OUTAGE) {
                    respond(exchange, 503, "{\"error\":\"secret-material\"}");
                    return;
                }
                double groundedness = mode == Mode.REJECT || mode == Mode.DOCUMENT_JUDGE_REJECT
                        || (mode == Mode.REFINE && call == 1) ? 0.1 : 0.95;
                respond(
                        exchange,
                        200,
                        json.writeValueAsString(Map.of(
                                "model",
                                "jev-contract-test",
                                "answers",
                                Map.of(
                                        "groundedness", Map.of("type", "noul", "noul", groundedness),
                                        "relevance", Map.of("type", "noul", "noul", 0.95),
                                        "advisory_only", Map.of("type", "noul", "noul", 0.99)),
                                "usage",
                                Map.of())));
            }
        }

        String chat(String content, String tool) {
            return chat(content, tool, tool == null ? "stop" : "tool_calls");
        }

        String chat(String content, String tool, String finishReason) {
            Map<String, Object> message = new java.util.LinkedHashMap<>();
            message.put("role", "assistant");
            message.put("content", content);
            if (tool != null) {
                message.put(
                        "tool_calls",
                        List.of(Map.of(
                                "id",
                                "call-contract",
                                "type",
                                "function",
                                "function",
                                Map.of("name", tool, "arguments", "{}"))));
            }
            return json.writeValueAsString(Map.of(
                    "id",
                    "chatcmpl-contract",
                    "object",
                    "chat.completion",
                    "created",
                    Instant.now().getEpochSecond(),
                    "model",
                    "contract-test",
                    "choices",
                    List.of(Map.of("index", 0, "message", message, "finish_reason", finishReason)),
                    "usage",
                    Map.of("prompt_tokens", 10, "completion_tokens", 10, "total_tokens", 20)));
        }

        void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ActorConfiguration {

        @Bean
        @Primary
        CurrentActor testActor() {
            return () -> new Actor(TENANT, "analyst", UserRole.ANALYST);
        }
    }
}
