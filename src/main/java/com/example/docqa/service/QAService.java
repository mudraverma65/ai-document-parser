package com.example.docqa.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    private final Logger log = LogManager.getLogger(QAService.class);

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
                You are analyzing the user's own uploaded documents. Answer using the
                context below as your primary source of truth.

                - You MAY synthesize or infer a reasonable answer by combining facts
                  that are present in the context (e.g. listing technologies mentioned
                  across multiple bullet points), even if no single sentence states
                  the answer directly.
                - If asked to compare, evaluate, or give an opinion (e.g. "which is
                  better", "is this good"), give a grounded, honest assessment based on
                  concrete differences visible in the context, and briefly note that
                  it's a judgment call rather than a fact.
                - Only say you don't know if the context truly contains nothing
                  relevant to the question — don't refuse just because the answer
                  requires connecting a few pieces of information.

                Context:
                %s

                Question: %s
                """.formatted(context, question);

        String answerText;
        try {
            log.debug("Sending prompt to Gemini ({} chars)", prompt.length());
            answerText = chatLanguageModel.generate(prompt);
        } catch (Exception e) {
            log.error("Gemini chat call failed. Prompt length={} chars, retrieved chunks={}",
                    prompt.length(), retrieved.size(), e);
            if (e.getCause() instanceof java.net.SocketTimeoutException) {
                throw new RuntimeException(
                        "Gemini took too long to respond. This can happen on broad questions " +
                                "that pull in a lot of context (like comparing whole documents). Try " +
                                "asking something more specific, or try again in a moment.", e);
            }
            throw e;
        }

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
