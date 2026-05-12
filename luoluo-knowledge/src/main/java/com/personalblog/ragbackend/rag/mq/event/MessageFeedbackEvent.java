package com.personalblog.ragbackend.rag.mq.event;

import java.io.Serial;
import java.io.Serializable;

public class MessageFeedbackEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long messageId;
    private Long userId;
    private Integer vote;
    private String reason;
    private String comment;
    private long submitTime;

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

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

    public long getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(long submitTime) {
        this.submitTime = submitTime;
    }
}
