package com.personalblog.ragbackend.infra.ai.convention;

public class RetrievedChunk {

    private String id;
    private String text;
    private Float score;

    public RetrievedChunk() {
    }

    public RetrievedChunk(String id, String text, Float score) {
        this.id = id;
        this.text = text;
        this.score = score;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Float getScore() {
        return score;
    }

    public void setScore(Float score) {
        this.score = score;
    }
}
