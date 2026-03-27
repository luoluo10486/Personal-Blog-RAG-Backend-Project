package com.personalblog.ragbackend.common.captcha.service;

import com.personalblog.ragbackend.common.auth.service.AuthDigestService;
import com.personalblog.ragbackend.common.captcha.dto.ImageCaptchaResponse;
import com.personalblog.ragbackend.common.redis.RedisClient;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 图形验证码服务，负责生成 SVG 验证码并通过 Redis 做一次性校验。
 */
@Service
public class ImageCaptchaService {
    private static final String CAPTCHA_KEY_PREFIX = "auth:image_captcha:";
    private static final char[] CAPTCHA_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private final RedisClient redisClient;
    private final AuthDigestService authDigestService;

    public ImageCaptchaService(RedisClient redisClient, AuthDigestService authDigestService) {
        this.redisClient = redisClient;
        this.authDigestService = authDigestService;
    }

    public ImageCaptchaResponse create(String namespace, int length, long ttlSeconds) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("图形验证码命名空间不能为空");
        }
        if (length < 4) {
            throw new IllegalArgumentException("图形验证码长度不能小于 4");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("图形验证码有效期必须大于 0");
        }

        String captchaKey = UUID.randomUUID().toString().replace("-", "");
        String code = randomCode(length);
        String digest = authDigestService.sha256Hex(normalizeCode(code));
        redisClient.set(buildCacheKey(namespace, captchaKey), digest, Duration.ofSeconds(ttlSeconds));

        String svg = buildSvg(code);
        String imageBase64 = Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return new ImageCaptchaResponse(captchaKey, imageBase64, ttlSeconds);
    }

    public boolean verifyAndConsume(String namespace, String captchaKey, String inputCode) {
        if (namespace == null || namespace.isBlank()
                || captchaKey == null || captchaKey.isBlank()
                || inputCode == null || inputCode.isBlank()) {
            return false;
        }

        String cacheKey = buildCacheKey(namespace, captchaKey);
        String inputDigest = authDigestService.sha256Hex(normalizeCode(inputCode));
        return redisClient.compareAndDelete(cacheKey, inputDigest);
    }

    private String buildCacheKey(String namespace, String captchaKey) {
        return CAPTCHA_KEY_PREFIX + namespace.trim().toLowerCase(Locale.ROOT) + ":" + captchaKey.trim();
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String randomCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            builder.append(CAPTCHA_CHARS[random.nextInt(CAPTCHA_CHARS.length)]);
        }
        return builder.toString();
    }

    private String buildSvg(String code) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder builder = new StringBuilder();
        builder.append("<svg xmlns='http://www.w3.org/2000/svg' width='160' height='48' viewBox='0 0 160 48'>");
        builder.append("<rect width='160' height='48' rx='8' ry='8' fill='#F7F4EA'/>");
        for (int i = 0; i < 6; i++) {
            builder.append("<line x1='").append(random.nextInt(0, 160))
                    .append("' y1='").append(random.nextInt(0, 48))
                    .append("' x2='").append(random.nextInt(0, 160))
                    .append("' y2='").append(random.nextInt(0, 48))
                    .append("' stroke='").append(randomColor())
                    .append("' stroke-width='1' stroke-opacity='0.45'/>");
        }
        for (int i = 0; i < 18; i++) {
            builder.append("<circle cx='").append(random.nextInt(0, 160))
                    .append("' cy='").append(random.nextInt(0, 48))
                    .append("' r='").append(random.nextInt(1, 3))
                    .append("' fill='").append(randomColor())
                    .append("' fill-opacity='0.35'/>");
        }
        for (int i = 0; i < code.length(); i++) {
            int x = 18 + i * 30 + random.nextInt(-2, 3);
            int y = 30 + random.nextInt(-4, 5);
            int rotate = random.nextInt(-18, 19);
            builder.append("<text x='").append(x)
                    .append("' y='").append(y)
                    .append("' font-size='26' font-family='Verdana, sans-serif' font-weight='700' fill='")
                    .append(randomColor())
                    .append("' transform='rotate(").append(rotate).append(" ").append(x).append(" ").append(y).append(")'>")
                    .append(code.charAt(i))
                    .append("</text>");
        }
        builder.append("</svg>");
        return builder.toString();
    }

    private String randomColor() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int red = random.nextInt(40, 160);
        int green = random.nextInt(40, 160);
        int blue = random.nextInt(40, 160);
        return String.format("#%02X%02X%02X", red, green, blue);
    }
}
