package com.personalblog.ragbackend.member.service.auth;

import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.domain.MemberUser;

/**
 * MemberLoginStrategy 定义登录策略的统一接口。
 */
public interface MemberLoginStrategy {
    String grantType();

    MemberUser authenticate(MemberLoginRequest request);
}

