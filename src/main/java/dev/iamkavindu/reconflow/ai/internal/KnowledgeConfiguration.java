package dev.iamkavindu.reconflow.ai.internal;

import java.time.Duration;
import org.springaicommunity.typesafe.JevBatchOptions;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.rag.JevDocumentFilter;
import org.springaicommunity.typesafe.rag.JevDocumentReranker;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"reconflow.ai.enabled", "reconflow.ai.knowledge-enabled"}, havingValue = "true")
class KnowledgeConfiguration {

    @Bean
    EmbeddingModel knowledgeEmbeddingModel(@Value("${reconflow.ai.embedding-model}") String model,
            @Value("${reconflow.ai.api-key}") String key,
            @Value("${reconflow.ai.base-url:https://api.openai.com/v1}") String url) {
        if (model.isBlank() || model.length() > 200) {
            throw new IllegalArgumentException("a bounded embedding model name is required");
        }
        return OpenAiEmbeddingModel.builder().options(OpenAiEmbeddingOptions.builder().model(model).apiKey(key)
                .baseUrl(url).dimensions(1536).timeout(Duration.ofSeconds(15)).maxRetries(0).build()).build();
    }

    @Bean
    PgVectorStore knowledgeVectorStore(JdbcTemplate jdbc, EmbeddingModel embedding) {
        return PgVectorStore.builder(jdbc, embedding).schemaName("ai").vectorTableName("knowledge_chunks")
                .dimensions(1536).initializeSchema(false).build();
    }

    @Bean
    RewriteQueryTransformer knowledgeQueryRewriter(ChatModel model) {
        return RewriteQueryTransformer.builder().chatClientBuilder(ChatClient.builder(model)).build();
    }

    @Bean
    MultiQueryExpander knowledgeQueryExpander(ChatModel model) {
        return MultiQueryExpander.builder().chatClientBuilder(ChatClient.builder(model))
                .numberOfQueries(3).includeOriginal(true).build();
    }

    @Bean
    JevDocumentFilter knowledgeDocumentFilter(TypeSafeClient client) {
        return JevDocumentFilter.builder(client).batchOptions(JevBatchOptions.ofConcurrency(2)).build();
    }

    @Bean
    JevDocumentReranker knowledgeDocumentReranker(TypeSafeClient client) {
        return JevDocumentReranker.builder(client).topK(3).minimumScore(0.5)
                .batchOptions(JevBatchOptions.ofConcurrency(2)).build();
    }
}
