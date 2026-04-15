package com.personalblog.ragbackend.member.service;

import com.personalblog.ragbackend.common.auth.dto.VerifyCodeIssueCommand;
import com.personalblog.ragbackend.common.auth.dto.VerifyCodeVerifyCommand;
import com.personalblog.ragbackend.common.auth.service.VerifyCodeService;
import com.personalblog.ragbackend.member.config.MemberProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class MemberVerifyCodeService {
    private static final Logger log = LoggerFactory.getLogger(MemberVerifyCodeService.class);
    private static final String VERIFY_CODE_NAMESPACE = "member_auth";
    private static final String LOGIN_BIZ_TYPE = "LOGIN";
    private static final String REGISTER_BIZ_TYPE = "REGISTER";
    private static final String SUBJECT_TYPE = "SYS_USER";

    private final VerifyCodeService verifyCodeService;
    private final MemberProperties memberProperties;

    public MemberVerifyCodeService(VerifyCodeService verifyCodeService, MemberProperties memberProperties) {
        this.verifyCodeService = verifyCodeService;
        this.memberProperties = memberProperties;
    }

    public boolean verifyAndConsume(String targetType, String targetValue, String inputCode) {
        return verifyAndConsume(LOGIN_BIZ_TYPE, targetType, targetValue, inputCode);
    }

    public boolean verifyAndConsume(String bizType, String targetType, String targetValue, String inputCode) {
        boolean passed = verifyCodeService.verifyAndConsume(new VerifyCodeVerifyCommand(
                VERIFY_CODE_NAMESPACE,
                resolveBizType(bizType),
                targetType,
                targetValue,
                inputCode,
                memberProperties.getMember().getAuth().isAllowMockVerifyCode(),
                memberProperties.getMember().getAuth().getMockVerifyCode()
        ));
        logPlaintextVerify(targetType, targetValue, inputCode, passed);
        return passed;
    }

    public boolean verifyRegisterOrLoginCodeAndConsume(String targetType, String targetValue, String inputCode) {
        return verifyAndConsume(REGISTER_BIZ_TYPE, targetType, targetValue, inputCode)
                || verifyAndConsume(LOGIN_BIZ_TYPE, targetType, targetValue, inputCode);
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
                resolveBizType(bizType),
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

    private String resolveBizType(String bizType) {
        if (bizType == null || bizType.isBlank()) {
            return LOGIN_BIZ_TYPE;
        }

        String normalized = bizType.trim().toUpperCase(Locale.ROOT);
        if (LOGIN_BIZ_TYPE.equals(normalized) || REGISTER_BIZ_TYPE.equals(normalized)) {
            return normalized;
        }
        throw new ResponseStatusException(BAD_REQUEST, "unsupported bizType: " + bizType);
    }

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
