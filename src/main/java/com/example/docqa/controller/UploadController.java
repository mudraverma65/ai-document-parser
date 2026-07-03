package com.example.docqa.controller;

import com.example.docqa.model.UploadResponse;
import com.example.docqa.service.DocumentIngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class UploadController {

    private final DocumentIngestionService ingestionService;

    public UploadController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * Upload a file (PDF, DOCX, TXT, etc.) to be parsed and indexed under a sessionId.
     * Max size is enforced both by Spring's multipart config and a hard check in the service.
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sessionId") String sessionId) {
        try {
            int chunks = ingestionService.ingestFile(file, sessionId);
            return ResponseEntity.ok(new UploadResponse(sessionId, file.getOriginalFilename(), chunks, "indexed"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to parse file: " + e.getMessage()));
        }
    }

    /**
     * Ingest a webpage by URL to be parsed and indexed under a sessionId.
     */
    @PostMapping("/ingest-url")
    public ResponseEntity<?> ingestUrl(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        String sessionId = body.get("sessionId");

        if (url == null || url.isBlank() || sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Both 'url' and 'sessionId' are required"));
        }

        try {
            int chunks = ingestionService.ingestUrl(url, sessionId);
            return ResponseEntity.ok(new UploadResponse(sessionId, url, chunks, "indexed"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch/parse URL: " + e.getMessage()));
        }
    }
}
