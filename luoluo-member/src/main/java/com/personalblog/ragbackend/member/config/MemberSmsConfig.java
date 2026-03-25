package com.personalblog.ragbackend.member.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.member.service.code.sms.AliyunMemberSmsSender;
import com.personalblog.ragbackend.member.service.code.sms.MemberSmsSender;
import com.personalblog.ragbackend.member.service.code.sms.MockMemberSmsSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MemberSmsConfig {

    @Bean
    public MemberSmsSender memberSmsSender(MemberProperties memberProperties, ObjectMapper objectMapper) {
        if (memberProperties.getMember().getSms().getAliyun().isEnabled()) {
            return new AliyunMemberSmsSender(memberProperties, objectMapper);
        }
        return new MockMemberSmsSender();
    }
}
