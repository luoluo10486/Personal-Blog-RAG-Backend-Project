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
        return NodeResult.builder().success(true).shouldContinue(true).build();
    }

    public static NodeResult ok(String message) {
        return NodeResult.builder().success(true).shouldContinue(true).message(message).build();
    }

    public static NodeResult skip(String reason) {
        return NodeResult.builder().success(true).shouldContinue(true).message("Skipped: " + reason).build();
    }

    public static NodeResult fail(Throwable error) {
        return NodeResult.builder()
                .success(false)
                .shouldContinue(false)
                .error(error)
                .message(error == null ? null : error.getMessage())
                .build();
    }

    public static NodeResult terminate(String reason) {
        return NodeResult.builder().success(true).shouldContinue(false).message(reason).build();
    }
}
