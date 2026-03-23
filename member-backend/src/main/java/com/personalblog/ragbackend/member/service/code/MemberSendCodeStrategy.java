package com.personalblog.ragbackend.member.service.code;

import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeRequest;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeResponse;

/**
 * 验证码发送策略接口，统一不同发送渠道的业务处理。
 */
public interface MemberSendCodeStrategy {
    String grantType();

    MemberSendVerifyCodeResponse send(MemberSendVerifyCodeRequest request);
}
