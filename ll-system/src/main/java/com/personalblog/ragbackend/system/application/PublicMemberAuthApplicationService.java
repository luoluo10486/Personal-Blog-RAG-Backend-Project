package com.personalblog.ragbackend.system.application;

import com.personalblog.ragbackend.common.captcha.service.CaptchaSendLimitService;
import com.personalblog.ragbackend.common.captcha.service.ImageCaptchaService;
import com.personalblog.ragbackend.config.AppProperties;
import com.personalblog.ragbackend.member.application.MemberAuthApplicationService;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeRequest;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 会员公共认证应用服务，负责公共接口层的图形验证码校验与发送频率控制。
 */
@Service
public class PublicMemberAuthApplicationService {
    private static final String IMAGE_CAPTCHA_NAMESPACE = "member_send_code";
    private static final String SEND_LIMIT_NAMESPACE = "member_send_code";

    private final MemberAuthApplicationService memberAuthApplicationService;
    private final ImageCaptchaService imageCaptchaService;
    private final CaptchaSendLimitService captchaSendLimitService;
    private final AppProperties appProperties;

    public PublicMemberAuthApplicationService(
            MemberAuthApplicationService memberAuthApplicationService,
            ImageCaptchaService imageCaptchaService,
            CaptchaSendLimitService captchaSendLimitService,
            AppProperties appProperties
    ) {
        this.memberAuthApplicationService = memberAuthApplicationService;
        this.imageCaptchaService = imageCaptchaService;
        this.captchaSendLimitService = captchaSendLimitService;
        this.appProperties = appProperties;
    }

    public MemberSendVerifyCodeResponse sendCode(MemberSendVerifyCodeRequest request) {
        verifyImageCaptcha(request);

        String grantType = normalizeGrantType(request.getGrantType());
        String targetType = grantType;
        String targetValue = normalizeTargetValue(grantType, request);
        long intervalSeconds = appProperties.getMember().getAuth().getVerifyCodeSendIntervalSeconds();

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
                    "验证码发送过于频繁，请" + Math.max(remainingSeconds, 1) + "秒后再试"
            );
        }

        try {
            return memberAuthApplicationService.sendCode(request);
        } catch (RuntimeException exception) {
            captchaSendLimitService.release(SEND_LIMIT_NAMESPACE, targetType, targetValue);
            throw exception;
        }
    }

    private void verifyImageCaptcha(MemberSendVerifyCodeRequest request) {
        if (!appProperties.getMember().getAuth().isImageCaptchaEnabled()) {
            return;
        }
        if (request.getCaptchaKey() == null || request.getCaptchaKey().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "captchaKey 不能为空");
        }
        if (request.getCaptchaCode() == null || request.getCaptchaCode().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "captchaCode 不能为空");
        }
        boolean passed = imageCaptchaService.verifyAndConsume(
                IMAGE_CAPTCHA_NAMESPACE,
                request.getCaptchaKey(),
                request.getCaptchaCode()
        );
        if (!passed) {
            throw new ResponseStatusException(BAD_REQUEST, "图形验证码错误或已失效");
        }
    }

    private String normalizeGrantType(String grantType) {
        if (grantType == null || grantType.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "grantType 不能为空");
        }
        return grantType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeTargetValue(String grantType, MemberSendVerifyCodeRequest request) {
        if ("sms".equals(grantType)) {
            if (request.getPhone() == null || request.getPhone().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "phone 不能为空");
            }
            return request.getPhone().trim();
        }
        if ("email".equals(grantType)) {
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "email 不能为空");
            }
            return request.getEmail().trim().toLowerCase(Locale.ROOT);
        }
        throw new ResponseStatusException(BAD_REQUEST, "不支持的 grantType：" + grantType);
    }
}
