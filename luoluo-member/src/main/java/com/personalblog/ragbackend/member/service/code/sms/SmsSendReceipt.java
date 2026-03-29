package com.personalblog.ragbackend.member.service.code.sms;

/**
 * 短信发送回执。
 * provider 表示短信渠道，templateId 表示模板标识，
 * requestId 表示请求流水号，debugCodeVisible 表示验证码是否可直接返回给调试端。
 */
public record SmsSendReceipt(
        String provider,
        String templateId,
        String requestId,
        boolean debugCodeVisible
) {
}
