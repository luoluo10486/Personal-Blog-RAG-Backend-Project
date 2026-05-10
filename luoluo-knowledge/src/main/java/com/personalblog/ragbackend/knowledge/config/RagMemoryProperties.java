package com.personalblog.ragbackend.knowledge.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.rag.memory")
public class RagMemoryProperties {
    private boolean summaryEnabled = true;
    @Min(1)
    private int titleMaxLength = 32;
    @Min(1)
    private int historyKeepTurns = 8;
    @Min(1)
    private int summaryStartTurns = 10;
    @Min(1)
    private int summaryMaxChars = 2000;

    public boolean isSummaryEnabled() {
        return summaryEnabled;
    }

    public void setSummaryEnabled(boolean summaryEnabled) {
        this.summaryEnabled = summaryEnabled;
    }

    public int getTitleMaxLength() {
        return titleMaxLength;
    }

    public void setTitleMaxLength(int titleMaxLength) {
        this.titleMaxLength = titleMaxLength;
    }

    public int getHistoryKeepTurns() {
        return historyKeepTurns;
    }

    public void setHistoryKeepTurns(int historyKeepTurns) {
        this.historyKeepTurns = historyKeepTurns;
    }

    public int getSummaryStartTurns() {
        return summaryStartTurns;
    }

    public void setSummaryStartTurns(int summaryStartTurns) {
        this.summaryStartTurns = summaryStartTurns;
    }

    public int getSummaryMaxChars() {
        return summaryMaxChars;
    }

    public void setSummaryMaxChars(int summaryMaxChars) {
        this.summaryMaxChars = summaryMaxChars;
    }
}
