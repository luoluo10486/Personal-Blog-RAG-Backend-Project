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
 * 短信验证码发送策略。
 */
@Service
public class SmsSendCodeStrategy implements MemberSendCodeStrategy {
    private final MemberVerifyCodeService memberVerifyCodeService;
    private final AppProperties appProperties;

    public SmsSendCodeStrategy(
            MemberVerifyCodeService memberVerifyCodeService,
            AppProperties appProperties
    ) {
        this.memberVerifyCodeService = memberVerifyCodeService;
        this.appProperties = appProperties;
    }

    @Override
    public String grantType() {
        return "sms";
    }

    @Override
    public MemberSendVerifyCodeResponse send(MemberSendVerifyCodeRequest request) {
        String phone = request.getPhone();
        if (phone == null || phone.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "phone 不能为空");
        }

        String normalizedPhone = phone.trim();
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String verifyCode = randomVerifyCode();
        memberVerifyCodeService.recordAndCache(
                "LOGIN",
                null,
                null,
                "sms",
                normalizedPhone,
                "SMS",
                null,
                "member-mock-sms",
                requestId,
                verifyCode,
                "会员短信验证码"
        );

        return new MemberSendVerifyCodeResponse(
                requestId,
                grantType(),
                maskPhone(normalizedPhone),
                appProperties.getMember().getAuth().getVerifyCodeTtlSeconds(),
                appProperties.getMember().getAuth().isAllowMockVerifyCode() ? verifyCode : null
        );
    }

    private String randomVerifyCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
    }

    private String maskPhone(String phone) {
        if (phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
