package com.personalblog.ragbackend.member.service.code.sms;

public record SmsSendReceipt(
        String provider,
        String templateId,
        String requestId,
        boolean debugCodeVisible
) {
}
