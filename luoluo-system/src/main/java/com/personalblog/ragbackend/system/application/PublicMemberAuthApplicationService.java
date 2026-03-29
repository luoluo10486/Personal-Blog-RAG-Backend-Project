package com.personalblog.ragbackend.system.application;

import com.personalblog.ragbackend.common.captcha.service.CaptchaSendLimitService;
import com.personalblog.ragbackend.common.captcha.service.ImageCaptchaService;
import com.personalblog.ragbackend.member.application.MemberAuthApplicationService;
import com.personalblog.ragbackend.member.config.MemberProperties;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeRequest;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 对外开放的会员认证应用服务。
 * 负责串联图形验证码校验、发送频控和验证码下发流程。
 */
@Service
public class PublicMemberAuthApplicationService {
    private static final String IMAGE_CAPTCHA_NAMESPACE = "member_send_code";
    private static final String SEND_LIMIT_NAMESPACE = "member_send_code";

    private final MemberAuthApplicationService memberAuthApplicationService;
    private final ImageCaptchaService imageCaptchaService;
    private final CaptchaSendLimitService captchaSendLimitService;
    private final MemberProperties memberProperties;

    public PublicMemberAuthApplicationService(
            MemberAuthApplicationService memberAuthApplicationService,
            ImageCaptchaService imageCaptchaService,
            CaptchaSendLimitService captchaSendLimitService,
            MemberProperties memberProperties
    ) {
        this.memberAuthApplicationService = memberAuthApplicationService;
        this.imageCaptchaService = imageCaptchaService;
        this.captchaSendLimitService = captchaSendLimitService;
        this.memberProperties = memberProperties;
    }

    /**
     * 对外发送验证码入口。
     */
    public MemberSendVerifyCodeResponse sendCode(MemberSendVerifyCodeRequest request) {
        verifyImageCaptcha(request);

        String grantType = normalizeGrantType(request.getGrantType());
        String targetType = grantType;
        String targetValue = normalizeTargetValue(grantType, request);
        long intervalSeconds = memberProperties.getMember().getAuth().getVerifyCodeSendIntervalSeconds();

        boolean acquired = captchaSendLimitService.tryAcquire(
                SEND_LIMIT_NAMESPACE,
                targetType,
                targetValue,
                intervalSeconds
        );
        if (!acquired) {
            long remainingSeconds = captchaSendLimitService.getRemainingSeconds(
                    SEND_LIMIT_NAMESPACE,
                    targetType,
                    targetValue
            );
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "verify code send is too frequent, retry after " + Math.max(remainingSeconds, 1) + " seconds"
            );
        }

        try {
            return memberAuthApplicationService.sendCode(request);
        } catch (RuntimeException exception) {
            captchaSendLimitService.release(SEND_LIMIT_NAMESPACE, targetType, targetValue);
            throw exception;
        }
    }

    /**
     * 校验图形验证码，并在通过后消费掉。
     */
    private void verifyImageCaptcha(MemberSendVerifyCodeRequest request) {
        if (!memberProperties.getMember().getAuth().isImageCaptchaEnabled()) {
            return;
        }
        if (request.getCaptchaKey() == null || request.getCaptchaKey().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "captchaKey must not be blank");
        }
        if (request.getCaptchaCode() == null || request.getCaptchaCode().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "captchaCode must not be blank");
        }
        boolean passed = imageCaptchaService.verifyAndConsume(
                IMAGE_CAPTCHA_NAMESPACE,
                request.getCaptchaKey(),
                request.getCaptchaCode()
        );
        if (!passed) {
            throw new ResponseStatusException(BAD_REQUEST, "image captcha is invalid or expired");
        }
    }

    /**
     * 规范化授权类型参数。
     */
    private String normalizeGrantType(String grantType) {
        if (grantType == null || grantType.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "grantType must not be blank");
        }
        return grantType.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 根据授权类型提取并规范化验证码发送目标。
     */
    private String normalizeTargetValue(String grantType, MemberSendVerifyCodeRequest request) {
        if ("sms".equals(grantType)) {
            if (request.getPhone() == null || request.getPhone().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "phone must not be blank");
            }
            return request.getPhone().trim();
        }
        if ("email".equals(grantType)) {
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "email must not be blank");
            }
            return request.getEmail().trim().toLowerCase(Locale.ROOT);
        }
        throw new ResponseStatusException(BAD_REQUEST, "unsupported grantType: " + grantType);
    }
}
