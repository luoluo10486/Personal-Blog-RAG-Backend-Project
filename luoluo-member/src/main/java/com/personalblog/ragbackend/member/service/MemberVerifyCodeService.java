package com.personalblog.ragbackend.member.service;

import com.personalblog.ragbackend.common.auth.dto.VerifyCodeIssueCommand;
import com.personalblog.ragbackend.common.auth.dto.VerifyCodeVerifyCommand;
import com.personalblog.ragbackend.common.auth.service.VerifyCodeService;
import com.personalblog.ragbackend.member.config.MemberProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 会员验证码服务。
 */
@Service
public class MemberVerifyCodeService {
    private static final Logger log = LoggerFactory.getLogger(MemberVerifyCodeService.class);
    private static final String VERIFY_CODE_NAMESPACE = "member_auth";
    private static final String LOGIN_BIZ_TYPE = "LOGIN";
    private static final String SUBJECT_TYPE = "SYS_USER";

    private final VerifyCodeService verifyCodeService;
    private final MemberProperties memberProperties;

    public MemberVerifyCodeService(VerifyCodeService verifyCodeService, MemberProperties memberProperties) {
        this.verifyCodeService = verifyCodeService;
        this.memberProperties = memberProperties;
    }

    /**
     * 校验验证码并在成功后消费掉。
     */
    public boolean verifyAndConsume(String targetType, String targetValue, String inputCode) {
        boolean passed = verifyCodeService.verifyAndConsume(new VerifyCodeVerifyCommand(
                VERIFY_CODE_NAMESPACE,
                LOGIN_BIZ_TYPE,
                targetType,
                targetValue,
                inputCode,
                memberProperties.getMember().getAuth().isAllowMockVerifyCode(),
                memberProperties.getMember().getAuth().getMockVerifyCode()
        ));
        logPlaintextVerify(targetType, targetValue, inputCode, passed);
        return passed;
    }

    /**
     * 记录验证码发送流水并写入缓存。
     */
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

    /**
     * 根据配置决定是否输出明文验证码校验日志。
     */
    private void logPlaintextVerify(String targetType, String targetValue, String inputCode, boolean passed) {
        if (!memberProperties.getMember().getAuth().isPlainVerifyCodeLogEnabled()) {
            return;
        }
        log.info(
                "Member verify code checked: namespace={}, targetType={}, targetValue={}, plainCode={}, passed={}",
                VERIFY_CODE_NAMESPACE,
                targetType,
                targetValue,
                inputCode,
                passed
        );
    }
}
