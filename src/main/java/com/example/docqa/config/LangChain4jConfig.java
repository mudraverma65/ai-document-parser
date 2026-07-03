package com.example.docqa.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Central place where all LangChain4j building blocks are wired up as Spring beans.
 * Swap OpenAiChatModel / OpenAiEmbeddingModel for other providers (Anthropic, Ollama, etc.)
 * without touching the rest of the app.
 */
@Configuration
public class LangChain4jConfig {

    @Value("${openai.api-key}")
    private String openAiApiKey;

    @Value("${openai.chat-model}")
    private String chatModelName;

    @Value("${openai.embedding-model}")
    private String embeddingModelName;

    @Value("${pgvector.host}")
    private String pgHost;

    @Value("${pgvector.port}")
    private Integer pgPort;

    @Value("${pgvector.database}")
    private String pgDatabase;

    @Value("${pgvector.user}")
    private String pgUser;

    @Value("${pgvector.password}")
    private String pgPassword;

    @Value("${pgvector.table}")
    private String pgTable;

    @Value("${pgvector.dimension}")
    private Integer pgDimension;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(chatModelName)
                .temperature(0.2)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(2)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(openAiApiKey)
                .modelName(embeddingModelName)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        // createTable=true will auto-create the pgvector table + extension on first boot.
        // Set to false once the schema exists in production to avoid repeated DDL checks.
        return PgVectorEmbeddingStore.builder()
                .host(pgHost)
                .port(pgPort)
                .database(pgDatabase)
                .user(pgUser)
                .password(pgPassword)
                .table(pgTable)
                .dimension(pgDimension)
                .createTable(true)
                .useIndex(true)
                .build();
    }
}
