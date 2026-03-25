package com.personalblog.ragbackend.common.mail.dto;

public record MailSendReceipt(
        String provider,
        String requestId,
        boolean debugPayloadVisible
) {
}
