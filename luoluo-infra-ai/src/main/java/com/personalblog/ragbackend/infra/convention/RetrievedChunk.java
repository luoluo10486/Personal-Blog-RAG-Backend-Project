package com.personalblog.ragbackend.infra.convention;

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

    public static Builder builder() {
        return new Builder();
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

    public static final class Builder {
        private String id;
        private String text;
        private Float score;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder score(Float score) {
            this.score = score;
            return this;
        }

        public RetrievedChunk build() {
            return new RetrievedChunk(id, text, score);
        }
    }
}
