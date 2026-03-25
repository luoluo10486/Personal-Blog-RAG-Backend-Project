package com.personalblog.ragbackend.member.service.auth.strategy;

import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.service.MemberUserService;
import com.personalblog.ragbackend.member.service.MemberVerifyCodeService;
import com.personalblog.ragbackend.member.service.auth.MemberLoginStrategy;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * 邮箱登录策略，处理邮箱和邮件验证码的认证流程。
 */
@Service
public class EmailLoginStrategy implements MemberLoginStrategy {
    private final MemberUserService memberUserService;
    private final MemberVerifyCodeService verifyCodeService;

    public EmailLoginStrategy(MemberUserService memberUserService, MemberVerifyCodeService verifyCodeService) {
        this.memberUserService = memberUserService;
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

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        boolean verified = verifyCodeService.verifyAndConsume("email", normalizedEmail, emailCode.trim());
        if (!verified) {
            throw new ResponseStatusException(UNAUTHORIZED, "邮箱验证码无效");
        }

        MemberUser user = memberUserService.findActiveByEmail(normalizedEmail);
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "未找到邮箱对应用户");
        }
        return user;
    }
}
