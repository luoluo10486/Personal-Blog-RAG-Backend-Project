package com.personalblog.ragbackend.member.service;

import com.personalblog.ragbackend.common.auth.dto.VerifyCodeIssueCommand;
import com.personalblog.ragbackend.common.auth.dto.VerifyCodeVerifyCommand;
import com.personalblog.ragbackend.common.auth.service.VerifyCodeService;
import com.personalblog.ragbackend.member.config.MemberProperties;
import org.springframework.stereotype.Service;

@Service
public class MemberVerifyCodeService {
    private static final String VERIFY_CODE_NAMESPACE = "member_auth";
    private static final String LOGIN_BIZ_TYPE = "LOGIN";
    private static final String SUBJECT_TYPE = "SYS_USER";

    private final VerifyCodeService verifyCodeService;
    private final MemberProperties memberProperties;

    public MemberVerifyCodeService(VerifyCodeService verifyCodeService, MemberProperties memberProperties) {
        this.verifyCodeService = verifyCodeService;
        this.memberProperties = memberProperties;
    }

    public boolean verifyAndConsume(String targetType, String targetValue, String inputCode) {
        return verifyCodeService.verifyAndConsume(new VerifyCodeVerifyCommand(
                VERIFY_CODE_NAMESPACE,
                LOGIN_BIZ_TYPE,
                targetType,
                targetValue,
                inputCode,
                memberProperties.getMember().getAuth().isAllowMockVerifyCode(),
                memberProperties.getMember().getAuth().getMockVerifyCode()
        ));
    }

    public void recordAndCache(
            String bizType,
            String bizId,
            String userType,
            String targetType,
            String targetValue,
            String messageChannel,
            String templateId,
            String provider,
            String requestId,
            String verifyCode,
            String remark
    ) {
        verifyCodeService.issue(new VerifyCodeIssueCommand(
                VERIFY_CODE_NAMESPACE,
                bizType,
                bizId,
                SUBJECT_TYPE,
                null,
                targetType,
                targetValue,
                messageChannel,
                templateId,
                provider,
                requestId,
                verifyCode,
                memberProperties.getMember().getAuth().getVerifyCodeTtlSeconds(),
                remark
        ));
    }
}
