package com.personalblog.ragbackend.member.service.code.sms;

/**
 * 会员短信发送器接口。
 */
public interface MemberSmsSender {
    /**
     * 发送登录验证码短信。
     *
     * @param phone 手机号
     * @param verifyCode 验证码
     * @param ttlSeconds 有效期，单位秒
     * @return 短信发送回执
     */
    SmsSendReceipt sendLoginCode(String phone, String verifyCode, long ttlSeconds);
}
