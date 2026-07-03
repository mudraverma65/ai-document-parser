package com.example.docqa.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Handles turning raw input (uploaded files or web URLs) into embedded,
 * searchable chunks tagged with a sessionId so different users/sessions
 * never see each other's documents.
 */
@Service
public class DocumentIngestionService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ApacheTikaDocumentParser tikaParser = new ApacheTikaDocumentParser();

    @Value("${app.max-chunk-size}")
    private int chunkSize;

    @Value("${app.max-chunk-overlap}")
    private int chunkOverlap;

    // Hard ceiling independent of the multipart limit, as a defense-in-depth check.
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024; // 10MB

    public DocumentIngestionService(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * Parses an uploaded file, chunks it, embeds each chunk, and stores it
     * with sessionId + sourceName metadata for later filtered retrieval.
     *
     * @return number of chunks indexed
     */
    public int ingestFile(MultipartFile file, String sessionId) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("File exceeds max allowed size of 10MB");
        }

        try (InputStream in = file.getInputStream()) {
            Document document = tikaParser.parse(in);
            document.metadata().put("sessionId", sessionId);
            document.metadata().put("sourceName", file.getOriginalFilename());
            document.metadata().put("sourceType", "file");
            return ingestDocument(document);
        }
    }

    /**
     * Fetches a webpage, strips it down to readable text, chunks, embeds,
     * and stores it with sessionId + sourceName (the URL) metadata.
     */
    public int ingestUrl(String url, String sessionId) throws IOException {
        org.jsoup.nodes.Document htmlDoc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; DocQaBot/1.0)")
                .timeout(15_000)
                .get();

        // Plain readable text, stripped of scripts/styles/nav markup.
        String text = htmlDoc.text();

        if (text.isBlank()) {
            throw new IllegalArgumentException("No readable text content found at URL: " + url);
        }
        // Basic size guard so a huge page can't blow up memory/embedding cost.
        if (text.getBytes().length > MAX_FILE_BYTES) {
            text = new String(text.getBytes(), 0, (int) MAX_FILE_BYTES);
        }

        Metadata metadata = new Metadata();
        metadata.put("sessionId", sessionId);
        metadata.put("sourceName", url);
        metadata.put("sourceType", "url");

        Document document = Document.from(text, metadata);
        return ingestDocument(document);
    }

    private int ingestDocument(Document document) {
        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);

        // Split first so we know exactly how many chunks we're about to index.
        int segmentCount = splitter.split(document).size();

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        ingestor.ingest(document);
        return segmentCount;
    }
}
