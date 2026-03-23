package com.personalblog.ragbackend.member.service.code.strategy;

import com.personalblog.ragbackend.config.AppProperties;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeRequest;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeResponse;
import com.personalblog.ragbackend.member.service.MemberVerifyCodeService;
import com.personalblog.ragbackend.member.service.code.MemberSendCodeStrategy;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 邮箱验证码发送策略。
 */
@Service
public class EmailSendCodeStrategy implements MemberSendCodeStrategy {
    private final MemberVerifyCodeService memberVerifyCodeService;
    private final AppProperties appProperties;

    public EmailSendCodeStrategy(
            MemberVerifyCodeService memberVerifyCodeService,
            AppProperties appProperties
    ) {
        this.memberVerifyCodeService = memberVerifyCodeService;
        this.appProperties = appProperties;
    }

    @Override
    public String grantType() {
        return "email";
    }

    @Override
    public MemberSendVerifyCodeResponse send(MemberSendVerifyCodeRequest request) {
        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "email 不能为空");
        }

        String normalizedEmail = email.trim().toLowerCase();
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String verifyCode = randomVerifyCode();
        memberVerifyCodeService.recordAndCache(
                "LOGIN",
                null,
                null,
                "email",
                normalizedEmail,
                "EMAIL",
                null,
                "member-mock-email",
                requestId,
                verifyCode,
                "会员邮箱验证码"
        );

        return new MemberSendVerifyCodeResponse(
                requestId,
                grantType(),
                maskEmail(normalizedEmail),
                appProperties.getMember().getAuth().getVerifyCodeTtlSeconds(),
                appProperties.getMember().getAuth().isAllowMockVerifyCode() ? verifyCode : null
        );
    }

    private String randomVerifyCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email;
        }
        return email.substring(0, 1) + "***" + email.substring(atIndex);
    }
}
