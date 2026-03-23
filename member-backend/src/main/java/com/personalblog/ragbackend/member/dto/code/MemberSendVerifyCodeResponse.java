package com.personalblog.ragbackend.member.dto.code;

/**
 * 验证码发送响应，返回发送流水和调试信息。
 */
public record MemberSendVerifyCodeResponse(
        String requestId,
        String grantType,
        String target,
        long expiresIn,
        String issuedCode
) {
}
