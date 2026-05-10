package com.personalblog.ragbackend.member.config;

import cn.dev33.satoken.stp.StpUtil;
import com.personalblog.ragbackend.common.context.LoginUser;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.service.MemberUserService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 将 Sa-Token 登录态映射到统一的用户上下文。
 */
@Configuration
public class MemberUserContextConfig implements WebMvcConfigurer {
    private final MemberUserService memberUserService;

    public MemberUserContextConfig(MemberUserService memberUserService) {
        this.memberUserService = memberUserService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new MemberUserContextInterceptor(memberUserService))
                .addPathPatterns("/**");
    }

    private static final class MemberUserContextInterceptor implements HandlerInterceptor {
        private final MemberUserService memberUserService;

        private MemberUserContextInterceptor(MemberUserService memberUserService) {
            this.memberUserService = memberUserService;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (request.getDispatcherType() == DispatcherType.ASYNC || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
                return true;
            }

            Long currentUserId = getCurrentUserId();
            if (currentUserId == null) {
                return true;
            }

            MemberUser user = memberUserService.findActiveById(currentUserId);
            if (user == null) {
                return true;
            }

            LoginUser loginUser = new LoginUser();
            loginUser.setUserId(String.valueOf(user.getUserId()));
            loginUser.setUsername(user.getUsername());
            loginUser.setDisplayName(user.getDisplayName());
            loginUser.setRole(user.getUserType());
            UserContext.set(loginUser);
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
            UserContext.clear();
        }

        private Long getCurrentUserId() {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) {
                return null;
            }
            try {
                return Long.parseLong(String.valueOf(loginId));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}
