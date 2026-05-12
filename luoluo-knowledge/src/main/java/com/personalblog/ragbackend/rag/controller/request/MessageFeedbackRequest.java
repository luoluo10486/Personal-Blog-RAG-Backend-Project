package com.personalblog.ragbackend.rag.controller.request;

public class MessageFeedbackRequest {
    private Integer vote;
    private String reason;
    private String comment;

    public Integer getVote() {
        return vote;
    }

    public void setVote(Integer vote) {
        this.vote = vote;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
