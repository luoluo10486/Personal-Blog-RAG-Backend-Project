package com.personalblog.ragbackend.common.auth.dto;

/**
 * 验证码校验命令，描述验证码核销所需的条件与开关。
 */
public record VerifyCodeVerifyCommand(
        String namespace,
        String bizType,
        String targetType,
        String targetValue,
        String inputCode,
        boolean allowMockCode,
        String mockCode
) {
}
