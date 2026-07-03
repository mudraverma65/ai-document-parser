package com.example.docqa.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * Answers a question using only the chunks that belong to the given sessionId,
 * so one user's uploaded documents are never leaked into another user's answers.
 */
@Service
public class QAService {

    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Value("${app.max-retrieved-segments}")
    private int maxResults;

    @Value("${app.min-similarity-score}")
    private double minScore;

    public QAService(ChatLanguageModel chatLanguageModel,
                      EmbeddingModel embeddingModel,
                      EmbeddingStore<TextSegment> embeddingStore) {
        this.chatLanguageModel = chatLanguageModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    public record Answer(String text, List<String> sourceExcerpts) {}

    public Answer ask(String sessionId, String question) {
        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(metadataKey("sessionId").isEqualTo(sessionId))
                .build();

        List<Content> retrieved = retriever.retrieve(dev.langchain4j.rag.query.Query.from(question));

        if (retrieved.isEmpty()) {
            return new Answer(
                    "I couldn't find anything relevant in your uploaded documents to answer that. " +
                            "Try uploading a file or webpage first, or rephrase your question.",
                    List.of());
        }

        String context = retrieved.stream()
                .map(c -> c.textSegment().text())
                .collect(Collectors.joining("\n---\n"));

        String prompt = """
                Answer the question using ONLY the context below. If the answer isn't
                contained in the context, say you don't know rather than guessing.

                Context:
                %s

                Question: %s
                """.formatted(context, question);

        String answerText = chatLanguageModel.generate(prompt);

        List<String> excerpts = retrieved.stream()
                .map(c -> truncate(c.textSegment().text(), 200))
                .collect(Collectors.toList());

        return new Answer(answerText, excerpts);
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
