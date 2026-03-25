package com.personalblog.ragbackend.common.auth.dto;

/**
 * 验证码发放命令，描述缓存和记录所需的业务上下文。
 */
public record VerifyCodeIssueCommand(
        String namespace,
        String bizType,
        String bizId,
        String subjectType,
        Long subjectId,
        String targetType,
        String targetValue,
        String channel,
        String templateId,
        String provider,
        String requestId,
        String verifyCode,
        long ttlSeconds,
        String remark
) {
}
