package com.personalblog.ragbackend.member.service.auth;

import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.model.MemberUser;

public interface MemberLoginStrategy {
    String grantType();

    MemberUser authenticate(MemberLoginRequest request);
}
