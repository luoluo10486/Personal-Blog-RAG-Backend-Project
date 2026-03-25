package com.personalblog.ragbackend.member.service.code.strategy;

import com.personalblog.ragbackend.common.mail.dto.MailSendReceipt;
import com.personalblog.ragbackend.common.mail.service.CommonMailSender;
import com.personalblog.ragbackend.member.config.MemberProperties;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeRequest;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeResponse;
import com.personalblog.ragbackend.member.service.MemberVerifyCodeService;
import com.personalblog.ragbackend.member.service.code.MemberSendCodeStrategy;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class EmailSendCodeStrategy implements MemberSendCodeStrategy {
    private final MemberVerifyCodeService memberVerifyCodeService;
    private final MemberProperties memberProperties;
    private final CommonMailSender commonMailSender;

    public EmailSendCodeStrategy(
            MemberVerifyCodeService memberVerifyCodeService,
            MemberProperties memberProperties,
            CommonMailSender commonMailSender
    ) {
        this.memberVerifyCodeService = memberVerifyCodeService;
        this.memberProperties = memberProperties;
        this.commonMailSender = commonMailSender;
    }

    @Override
    public String grantType() {
        return "email";
    }

    @Override
    public MemberSendVerifyCodeResponse send(MemberSendVerifyCodeRequest request) {
        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "email must not be blank");
        }

        String normalizedEmail = email.trim().toLowerCase();
        String verifyCode = randomVerifyCode();
        long ttlSeconds = memberProperties.getMember().getAuth().getVerifyCodeTtlSeconds();
        MailSendReceipt receipt = commonMailSender.sendText(
                normalizedEmail,
                memberProperties.getMember().getEmail().getLoginSubject(),
                buildContent(verifyCode, ttlSeconds)
        );
        if (!memberProperties.getMember().getAuth().isAllowMockVerifyCode() && receipt.debugPayloadVisible()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Email sender is not configured");
        }

        memberVerifyCodeService.recordAndCache(
                "LOGIN",
                null,
                null,
                "email",
                normalizedEmail,
                "EMAIL",
                null,
                receipt.provider(),
                receipt.requestId(),
                verifyCode,
                "member email verify code"
        );

        return new MemberSendVerifyCodeResponse(
                receipt.requestId(),
                grantType(),
                maskEmail(normalizedEmail),
                ttlSeconds,
                memberProperties.getMember().getAuth().isAllowMockVerifyCode() && receipt.debugPayloadVisible() ? verifyCode : null
        );
    }

    private String randomVerifyCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
    }

    private String buildContent(String verifyCode, long ttlSeconds) {
        long ttlMinutes = Math.max(1, (ttlSeconds + 59) / 60);
        return memberProperties.getMember().getEmail().getLoginContentTemplate().formatted(verifyCode, ttlMinutes);
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email;
        }
        return email.substring(0, 1) + "***" + email.substring(atIndex);
    }
}
