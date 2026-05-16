package com.personalblog.ragbackend.knowledge.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentStatus {
    PENDING("pending"),
    RUNNING("running"),
    FAILED("failed"),
    SUCCESS("success");

    private final String code;
}
