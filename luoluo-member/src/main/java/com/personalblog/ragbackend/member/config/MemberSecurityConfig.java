package com.personalblog.ragbackend.member.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 会员模块安全相关配置。
 */
@Configuration
public class MemberSecurityConfig {

    /**
     * 提供密码编码器，用于会员密码加密和校验。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
