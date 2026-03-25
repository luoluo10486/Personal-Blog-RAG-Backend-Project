package com.personalblog.ragbackend.common.auth.dto;

import java.time.LocalDateTime;

/**
 * 会话创建结果，返回调用方需要暴露的令牌信息。
 */
public record AuthSessionResult(
        Long sessionId,
        String token,
        LocalDateTime expiresAt
) {
}
