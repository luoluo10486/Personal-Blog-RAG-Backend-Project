package com.personalblog.ragbackend.ingestion.domain.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeResult {
    private boolean success;
    private boolean shouldContinue;
    private String message;
    private Throwable error;

    public static NodeResult ok() {
        return new NodeResult(true, true, null, null);
    }

    public static NodeResult ok(String message) {
        return new NodeResult(true, true, message, null);
    }

    public static NodeResult skip(String reason) {
        return new NodeResult(true, true, "Skipped: " + reason, null);
    }

    public static NodeResult fail(Throwable error) {
        return new NodeResult(false, false, error == null ? null : error.getMessage(), error);
    }

    public static NodeResult terminate(String reason) {
        return new NodeResult(true, false, reason, null);
    }
}
