package com.example.docqa.model;

import java.util.List;

public class ChatResponse {

    private String answer;
    private List<String> sourceExcerpts;

    public ChatResponse() {
    }

    public ChatResponse(String answer, List<String> sourceExcerpts) {
        this.answer = answer;
        this.sourceExcerpts = sourceExcerpts;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<String> getSourceExcerpts() {
        return sourceExcerpts;
    }

    public void setSourceExcerpts(List<String> sourceExcerpts) {
        this.sourceExcerpts = sourceExcerpts;
    }
}
