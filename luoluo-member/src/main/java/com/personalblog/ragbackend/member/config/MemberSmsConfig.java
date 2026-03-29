package com.personalblog.ragbackend.member.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.member.service.code.sms.AliyunMemberSmsSender;
import com.personalblog.ragbackend.member.service.code.sms.MemberSmsSender;
import com.personalblog.ragbackend.member.service.code.sms.MockMemberSmsSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 会员短信发送配置。
 */
@Configuration
public class MemberSmsConfig {

    /**
     * 根据配置装配真实短信发送器或 mock 发送器。
     */
    @Bean
    public MemberSmsSender memberSmsSender(MemberProperties memberProperties, ObjectMapper objectMapper) {
        if (memberProperties.getMember().getSms().getAliyun().isEnabled()) {
            return new AliyunMemberSmsSender(memberProperties, objectMapper);
        }
        return new MockMemberSmsSender();
    }
}
