package com.example.docqa.model;

public class UploadResponse {

    private String sessionId;
    private String sourceName;
    private int chunksIndexed;
    private String status;

    public UploadResponse() {
    }

    public UploadResponse(String sessionId, String sourceName, int chunksIndexed, String status) {
        this.sessionId = sessionId;
        this.sourceName = sourceName;
        this.chunksIndexed = chunksIndexed;
        this.status = status;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public int getChunksIndexed() {
        return chunksIndexed;
    }

    public void setChunksIndexed(int chunksIndexed) {
        this.chunksIndexed = chunksIndexed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
