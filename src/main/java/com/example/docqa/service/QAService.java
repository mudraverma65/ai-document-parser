package com.example.docqa.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * Answers a question using only the chunks that belong to the given sessionId,
 * so one user's uploaded documents are never leaked into another user's answers.
 * Also keeps a rolling chat memory per sessionId so follow-up questions
 * ("what about the second one?") have conversational context.
 */
@Service
public class QAService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QAService.class);

    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    // One chat memory per session. A new sessionId (e.g. after "new session" in the
    // UI) simply gets a fresh entry here — nothing to clear explicitly for that case.
    private final Map<String, ChatMemory> sessionMemories = new ConcurrentHashMap<>();

    @Value("${app.max-retrieved-segments}")
    private int maxResults;

    @Value("${app.min-similarity-score}")
    private double minScore;

    @Value("${app.max-memory-messages}")
    private int maxMemoryMessages;

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

        String userMessageText = """
                Answer the question using the context below as your primary source of
                truth, and the earlier conversation (if any) for follow-up context.

                - You MAY synthesize or infer a reasonable answer by combining facts
                  that are present in the context, even if no single sentence states
                  the answer directly.
                - If asked to compare, evaluate, or give an opinion, give a grounded,
                  honest assessment based on concrete differences visible in the
                  context, and briefly note that it's a judgment call rather than fact.
                - Only say you don't know if the context truly contains nothing
                  relevant — don't refuse just because the answer requires connecting
                  a few pieces of information.
                - Keep your answer focused and reasonably concise — a clear, direct
                  response rather than an exhaustive essay.

                Context:
                %s

                Question: %s
                """.formatted(context, question);

        ChatMemory memory = sessionMemories.computeIfAbsent(sessionId,
                id -> MessageWindowChatMemory.withMaxMessages(maxMemoryMessages));

        memory.add(UserMessage.from(userMessageText));

        String answerText;
        try {
            log.debug("Sending prompt to Gemini ({} chars, {} messages in memory)",
                    userMessageText.length(), memory.messages().size());
            Response<AiMessage> response = chatLanguageModel.generate(memory.messages());
            answerText = response.content().text();
            memory.add(response.content());
        } catch (Exception e) {
            log.error("Gemini chat call failed. Prompt length={} chars, retrieved chunks={}",
                    userMessageText.length(), retrieved.size(), e);
            if (e.getCause() instanceof java.net.SocketTimeoutException) {
                throw new RuntimeException(
                        "Gemini took too long to respond. This can happen on broad questions " +
                                "that pull in a lot of context. Try asking something more specific, " +
                                "or try again in a moment.", e);
            }
            if (e.getMessage() != null && e.getMessage().contains("RESOURCE_EXHAUSTED")) {
                throw new RuntimeException(
                        "You've hit Gemini's free-tier rate limit (a cap on requests per " +
                                "minute). Wait about 30-60 seconds and try again - this isn't a bug, " +
                                "just the free tier's usage cap.", e);
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