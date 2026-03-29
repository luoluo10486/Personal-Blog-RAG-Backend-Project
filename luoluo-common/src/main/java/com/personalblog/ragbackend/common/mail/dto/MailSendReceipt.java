package com.personalblog.ragbackend.common.mail.dto;

/**
 * 邮件发送回执。
 * provider 表示发送渠道，requestId 表示本次请求标识，
 * debugPayloadVisible 表示当前是否处于 mock/调试可见模式。
 */
public record MailSendReceipt(
        String provider,
        String requestId,
        boolean debugPayloadVisible
) {
}
