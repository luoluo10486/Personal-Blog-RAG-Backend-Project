package com.personalblog.ragbackend.knowledge.filter;

import com.personalblog.ragbackend.knowledge.config.RagSemaphoreProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 文件上传限流 Filter
 * 在 multipart 解析之前拦截，防止临时文件过多
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class UploadRateLimitFilter extends OncePerRequestFilter {

    private static final String UPLOAD_PATH_PATTERN = "/knowledge-base/";
    private static final String UPLOAD_PATH_SUFFIX = "/docs/upload";

    private final RedissonClient redissonClient;
    private final RagSemaphoreProperties semaphoreProperties;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (!isUploadRequest(request)) {
            chain.doFilter(request, response);
            return;
        }

        RagSemaphoreProperties.PermitExpirableConfig config = semaphoreProperties.getDocumentUpload();
        RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(config.getName());

        String permitId = null;
        try {
            permitId = semaphore.tryAcquire(
                    config.getMaxWaitSeconds(),
                    config.getLeaseSeconds(),
                    TimeUnit.SECONDS
            );

            if (permitId == null) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":\"429\",\"message\":\"当前上传人数过多，请稍后再试\"}");
                return;
            }

            chain.doFilter(request, response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            response.setStatus(500);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"500\",\"message\":\"获取上传许可失败\"}");
        } finally {
            if (permitId != null) {
                boolean released = semaphore.tryRelease(permitId);
                if (!released) {
                    log.warn("upload permit already expired or released, permitId={}", permitId);
                }
            }
        }
    }

    private boolean isUploadRequest(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.contains(UPLOAD_PATH_PATTERN) && uri.endsWith(UPLOAD_PATH_SUFFIX);
    }
}
