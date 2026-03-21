package com.personalblog.ragbackend.member.service.auth.strategy;

import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.mapper.MemberUserMapper;
import com.personalblog.ragbackend.member.service.MemberVerifyCodeService;
import com.personalblog.ragbackend.member.service.auth.MemberLoginStrategy;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * EmailLoginStrategy 登录策略实现类，负责处理对应方式的认证逻辑。
 */
@Service
public class EmailLoginStrategy implements MemberLoginStrategy {
    private final MemberUserMapper memberUserMapper;
    private final MemberVerifyCodeService verifyCodeService;

    public EmailLoginStrategy(MemberUserMapper memberUserMapper, MemberVerifyCodeService verifyCodeService) {
        this.memberUserMapper = memberUserMapper;
        this.verifyCodeService = verifyCodeService;
    }

    @Override
    public String grantType() {
        return "email";
    }

    @Override
    public MemberUser authenticate(MemberLoginRequest request) {
        String email = request.getEmail();
        String emailCode = request.getEmailCode();
        if (email == null || email.isBlank() || emailCode == null || emailCode.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "email 和 emailCode 不能为空");
        }

        boolean verified = verifyCodeService.verifyAndConsume("email", email.trim(), emailCode.trim());
        if (!verified) {
            throw new ResponseStatusException(UNAUTHORIZED, "邮箱验证码无效");
        }

        MemberUser user = memberUserMapper.selectActiveByEmail(email.trim());
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "未找到邮箱对应用户");
        }
        return user;
    }
}

