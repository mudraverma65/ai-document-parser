package com.example.docqa.model;

import jakarta.validation.constraints.NotBlank;

public class ChatRequest {

    @NotBlank(message = "sessionId is required")
    private String sessionId;

    @NotBlank(message = "question is required")
    private String question;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
