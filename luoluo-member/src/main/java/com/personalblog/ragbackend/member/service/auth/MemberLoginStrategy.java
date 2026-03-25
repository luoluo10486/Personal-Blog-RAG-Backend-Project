package com.personalblog.ragbackend.member.service.auth;

import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;

/**
 * 登录策略接口，统一不同认证方式的用户校验流程。
 */
public interface MemberLoginStrategy {
    String grantType();

    MemberUser authenticate(MemberLoginRequest request);
}
