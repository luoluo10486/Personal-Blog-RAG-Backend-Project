package com.personalblog.ragbackend.system.controller.pub;

import cn.dev33.satoken.annotation.SaIgnore;
import com.personalblog.ragbackend.common.captcha.dto.ImageCaptchaResponse;
import com.personalblog.ragbackend.common.captcha.service.ImageCaptchaService;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公共图形验证码控制器，对外提供匿名访问的图形验证码获取接口。
 */
@SaIgnore
@RestController
@RequestMapping("/luoluo/system/public/captcha")
public class PublicCaptchaController {
    private static final String IMAGE_CAPTCHA_NAMESPACE = "member_send_code";

    private final ImageCaptchaService imageCaptchaService;
    private final AppProperties appProperties;

    public PublicCaptchaController(ImageCaptchaService imageCaptchaService, AppProperties appProperties) {
        this.imageCaptchaService = imageCaptchaService;
        this.appProperties = appProperties;
    }

    @GetMapping("/image")
    public R<ImageCaptchaResponse> image() {
        if (!appProperties.getMember().getAuth().isImageCaptchaEnabled()) {
            return R.ok("图形验证码未启用", new ImageCaptchaResponse(null, null, 0));
        }
        return R.ok("获取图形验证码成功", imageCaptchaService.create(
                IMAGE_CAPTCHA_NAMESPACE,
                appProperties.getMember().getAuth().getImageCaptchaLength(),
                appProperties.getMember().getAuth().getImageCaptchaTtlSeconds()
        ));
    }
}
