package com.personalblog.ragbackend.member.service.code.sms;

public interface MemberSmsSender {
    SmsSendReceipt sendLoginCode(String phone, String verifyCode, long ttlSeconds);
}
