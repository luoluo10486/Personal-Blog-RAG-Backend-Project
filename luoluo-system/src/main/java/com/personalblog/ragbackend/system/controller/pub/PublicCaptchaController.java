package com.personalblog.ragbackend.system.controller.pub;

import cn.dev33.satoken.annotation.SaIgnore;
import com.personalblog.ragbackend.common.captcha.dto.ImageCaptchaResponse;
import com.personalblog.ragbackend.common.captcha.service.ImageCaptchaService;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.member.config.MemberProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SaIgnore
@RestController
@RequestMapping("/luoluo/system/public/captcha")
public class PublicCaptchaController {
    private static final String IMAGE_CAPTCHA_NAMESPACE = "member_send_code";

    private final ImageCaptchaService imageCaptchaService;
    private final MemberProperties memberProperties;

    public PublicCaptchaController(ImageCaptchaService imageCaptchaService, MemberProperties memberProperties) {
        this.imageCaptchaService = imageCaptchaService;
        this.memberProperties = memberProperties;
    }

    @GetMapping("/image")
    public R<ImageCaptchaResponse> image() {
        if (!memberProperties.getMember().getAuth().isImageCaptchaEnabled()) {
            return R.ok("image captcha is disabled", new ImageCaptchaResponse(null, null, 0));
        }
        return R.ok("image captcha created", imageCaptchaService.create(
                IMAGE_CAPTCHA_NAMESPACE,
                memberProperties.getMember().getAuth().getImageCaptchaLength(),
                memberProperties.getMember().getAuth().getImageCaptchaTtlSeconds()
        ));
    }
}
