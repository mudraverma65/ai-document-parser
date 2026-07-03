package com.example.docqa.controller;

import com.example.docqa.model.ChatRequest;
import com.example.docqa.model.ChatResponse;
import com.example.docqa.service.QAService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final QAService qaService;

    public ChatController(QAService qaService) {
        this.qaService = qaService;
    }

    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> ask(@Valid @RequestBody ChatRequest request) {
        QAService.Answer answer = qaService.ask(request.getSessionId(), request.getQuestion());
        return ResponseEntity.ok(new ChatResponse(answer.text(), answer.sourceExcerpts()));
    }
}
