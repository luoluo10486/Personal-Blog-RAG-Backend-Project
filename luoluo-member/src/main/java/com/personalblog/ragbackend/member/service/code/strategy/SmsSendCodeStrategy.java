package com.personalblog.ragbackend.member.service.code.strategy;

import com.personalblog.ragbackend.member.config.MemberProperties;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeRequest;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeResponse;
import com.personalblog.ragbackend.member.service.MemberVerifyCodeService;
import com.personalblog.ragbackend.member.service.code.MemberSendCodeStrategy;
import com.personalblog.ragbackend.member.service.code.sms.MemberSmsSender;
import com.personalblog.ragbackend.member.service.code.sms.SmsSendReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/**
 * 短信验证码发送策略。
 */
@Service
public class SmsSendCodeStrategy implements MemberSendCodeStrategy {
    private static final Logger log = LoggerFactory.getLogger(SmsSendCodeStrategy.class);
    private final MemberVerifyCodeService memberVerifyCodeService;
    private final MemberProperties memberProperties;
    private final MemberSmsSender memberSmsSender;

    public SmsSendCodeStrategy(
            MemberVerifyCodeService memberVerifyCodeService,
            MemberProperties memberProperties,
            MemberSmsSender memberSmsSender
    ) {
        this.memberVerifyCodeService = memberVerifyCodeService;
        this.memberProperties = memberProperties;
        this.memberSmsSender = memberSmsSender;
    }

    /**
     * 返回当前策略支持的授权方式。
     */
    @Override
    public String grantType() {
        return "sms";
    }

    /**
     * 发送短信验证码并记录验证码流水。
     */
    @Override
    public MemberSendVerifyCodeResponse send(MemberSendVerifyCodeRequest request) {
        String phone = request.getPhone();
        if (phone == null || phone.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "phone must not be blank");
        }

        String normalizedPhone = phone.trim();
        String verifyCode = randomVerifyCode();
        long ttlSeconds = memberProperties.getMember().getAuth().getVerifyCodeTtlSeconds();
        SmsSendReceipt receipt = memberSmsSender.sendLoginCode(normalizedPhone, verifyCode, ttlSeconds);
        logPlaintextCode(normalizedPhone, verifyCode, ttlSeconds, receipt.requestId());
        if (!memberProperties.getMember().getAuth().isAllowMockVerifyCode() && receipt.debugCodeVisible()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "SMS sender is not configured");
        }

        memberVerifyCodeService.recordAndCache(
                request.getBizType(),
                null,
                null,
                "sms",
                normalizedPhone,
                "SMS",
                receipt.templateId(),
                receipt.provider(),
                receipt.requestId(),
                verifyCode,
                "member sms verify code"
        );

        return new MemberSendVerifyCodeResponse(
                receipt.requestId(),
                grantType(),
                maskPhone(normalizedPhone),
                ttlSeconds,
                memberProperties.getMember().getAuth().isAllowMockVerifyCode() && receipt.debugCodeVisible() ? verifyCode : null
        );
    }

    /**
     * 生成 6 位随机验证码。
     */
    private String randomVerifyCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
    }

    /**
     * 对手机号做脱敏展示。
     */
    private String maskPhone(String phone) {
        if (phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 根据配置决定是否输出明文验证码日志。
     */
    private void logPlaintextCode(String phone, String verifyCode, long ttlSeconds, String requestId) {
        if (!memberProperties.getMember().getAuth().isPlainVerifyCodeLogEnabled()) {
            return;
        }
        log.info(
                "Member sms verify code prepared: phone={}, plainCode={}, ttlSeconds={}, requestId={}",
                phone,
                verifyCode,
                ttlSeconds,
                requestId
        );
    }
}
