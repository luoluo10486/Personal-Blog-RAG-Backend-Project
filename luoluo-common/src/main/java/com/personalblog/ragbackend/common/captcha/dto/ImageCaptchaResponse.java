package com.personalblog.ragbackend.common.captcha.dto;

/**
 * 图形验证码响应，返回验证码键、图片内容和有效时长。
 */
public record ImageCaptchaResponse(
        String captchaKey,
        String imageBase64,
        long expiresIn
) {
}
