package com.example.docqa.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Central place where all LangChain4j building blocks are wired up as Spring beans.
 * Uses Google Gemini (free tier via Google AI Studio) for chat + embeddings —
 * swap these beans for another provider without touching the rest of the app.
 */
@Configuration
public class LangChain4jConfig {

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${gemini.chat-model}")
    private String chatModelName;

    @Value("${gemini.embedding-model}")
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
        return GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName(chatModelName)
                .temperature(0.2)
                .timeout(Duration.ofSeconds(180))
                .maxRetries(0)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return GoogleAiEmbeddingModel.builder()
                .apiKey(geminiApiKey)
                .modelName(embeddingModelName)
                .maxRetries(3)
                .outputDimensionality(pgDimension)
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
                .useIndex(false)
                .build();
    }
}