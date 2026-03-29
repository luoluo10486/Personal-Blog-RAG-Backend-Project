package com.personalblog.ragbackend.member.service.code.sms;

import java.util.UUID;

/**
 * 本地调试使用的 mock 短信发送器。
 */
public class MockMemberSmsSender implements MemberSmsSender {
    /**
     * 返回模拟短信发送回执，不真正调用外部短信服务。
     */
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
