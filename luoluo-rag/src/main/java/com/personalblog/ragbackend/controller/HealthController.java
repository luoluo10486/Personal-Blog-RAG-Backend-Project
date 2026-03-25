package com.personalblog.ragbackend.controller;

import com.personalblog.ragbackend.common.web.domain.R;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * HealthController 控制器，负责处理对外 HTTP 请求。
 */
@RestController
@RequestMapping("${app.api-prefix}")
public class HealthController {
    @Value("${spring.application.name}")
    private String appName;

    @GetMapping("/health")
    public R<Map<String, String>> health() {
        return R.ok("服务正常", Map.of(
                "status", "ok",
                "service", appName
        ));
    }
}

