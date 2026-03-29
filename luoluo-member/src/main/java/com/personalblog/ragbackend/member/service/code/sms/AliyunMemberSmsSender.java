package com.personalblog.ragbackend.member.service.code.sms;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.member.config.MemberProperties;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

/**
 * 基于阿里云短信服务的会员短信发送器。
 */
public class AliyunMemberSmsSender implements MemberSmsSender {
    private static final String PRODUCT = "Dysmsapi";

    private final ObjectMapper objectMapper;
    private final MemberProperties.Aliyun aliyunProperties;
    private final IAcsClient acsClient;

    public AliyunMemberSmsSender(MemberProperties memberProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.aliyunProperties = memberProperties.getMember().getSms().getAliyun();
        validateProperties(aliyunProperties);
        this.acsClient = createClient(aliyunProperties);
    }

    /**
     * 发送登录验证码短信。
     */
    @Override
    public SmsSendReceipt sendLoginCode(String phone, String verifyCode, long ttlSeconds) {
        SendSmsRequest request = new SendSmsRequest();
        request.setPhoneNumbers(phone);
        request.setSignName(aliyunProperties.getSignName());
        request.setTemplateCode(aliyunProperties.getLoginTemplateCode());
        request.setTemplateParam(buildTemplateParamJson(verifyCode, ttlSeconds));

        try {
            SendSmsResponse response = acsClient.getAcsResponse(request);
            if (response == null || response.getCode() == null || !"OK".equalsIgnoreCase(response.getCode())) {
                throw new ResponseStatusException(
                        BAD_GATEWAY,
                        "Aliyun SMS send failed: " + (response == null ? "empty response" : response.getMessage())
                );
            }

            return new SmsSendReceipt(
                    "aliyun-dysmsapi",
                    aliyunProperties.getLoginTemplateCode(),
                    response.getRequestId() == null || response.getRequestId().isBlank()
                            ? UUID.randomUUID().toString().replace("-", "")
                            : response.getRequestId(),
                    false
            );
        } catch (ClientException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Aliyun SMS request failed", ex);
        }
    }

    /**
     * 创建阿里云短信客户端。
     */
    private IAcsClient createClient(MemberProperties.Aliyun properties) {
        try {
            DefaultProfile.addEndpoint(
                    properties.getRegionId(),
                    properties.getRegionId(),
                    PRODUCT,
                    properties.getEndpoint()
            );
            IClientProfile profile = DefaultProfile.getProfile(
                    properties.getRegionId(),
                    properties.getAccessKeyId(),
                    properties.getAccessKeySecret()
            );
            return new DefaultAcsClient(profile);
        } catch (ClientException ex) {
            throw new IllegalStateException("Failed to initialize Aliyun SMS client", ex);
        }
    }

    /**
     * 构造模板参数 JSON。
     */
    private String buildTemplateParamJson(String verifyCode, long ttlSeconds) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(aliyunProperties.getCodeParamName(), verifyCode);
        params.put(aliyunProperties.getTtlMinutesParamName(), ttlMinutes(ttlSeconds));

        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize Aliyun SMS template params", ex);
        }
    }

    /**
     * 将秒数换算为向上取整的分钟数。
     */
    private long ttlMinutes(long ttlSeconds) {
        return Math.max(1, (ttlSeconds + 59) / 60);
    }

    /**
     * 校验阿里云短信配置是否完整。
     */
    private void validateProperties(MemberProperties.Aliyun properties) {
        requireText(properties.getAccessKeyId(), "app.member.sms.aliyun.access-key-id");
        requireText(properties.getAccessKeySecret(), "app.member.sms.aliyun.access-key-secret");
        requireText(properties.getRegionId(), "app.member.sms.aliyun.region-id");
        requireText(properties.getEndpoint(), "app.member.sms.aliyun.endpoint");
        requireText(properties.getSignName(), "app.member.sms.aliyun.sign-name");
        requireText(properties.getLoginTemplateCode(), "app.member.sms.aliyun.login-template-code");
        requireText(properties.getCodeParamName(), "app.member.sms.aliyun.code-param-name");
        requireText(properties.getTtlMinutesParamName(), "app.member.sms.aliyun.ttl-minutes-param-name");
    }

    /**
     * 校验指定配置项不能为空。
     */
    private void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured when Aliyun SMS is enabled");
        }
    }
}
