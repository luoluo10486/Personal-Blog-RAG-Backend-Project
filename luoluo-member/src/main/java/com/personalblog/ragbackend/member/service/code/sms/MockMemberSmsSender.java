package com.personalblog.ragbackend.member.service.code.sms;

import java.util.UUID;

public class MockMemberSmsSender implements MemberSmsSender {
    @Override
    public SmsSendReceipt sendLoginCode(String phone, String verifyCode, long ttlSeconds) {
        return new SmsSendReceipt(
                "member-mock-sms",
                null,
                UUID.randomUUID().toString().replace("-", ""),
                true
        );
    }
}
