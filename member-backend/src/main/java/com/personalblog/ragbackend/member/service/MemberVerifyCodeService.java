package com.personalblog.ragbackend.member.service;

import com.personalblog.ragbackend.config.AppProperties;
import com.personalblog.ragbackend.member.domain.MemberVerifyCode;
import com.personalblog.ragbackend.member.mapper.MemberVerifyCodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * MemberVerifyCodeService 服务类，封装业务处理逻辑。
 */
@Service
public class MemberVerifyCodeService {
    private final MemberVerifyCodeMapper memberVerifyCodeMapper;
    private final AppProperties appProperties;

    public MemberVerifyCodeService(MemberVerifyCodeMapper memberVerifyCodeMapper, AppProperties appProperties) {
        this.memberVerifyCodeMapper = memberVerifyCodeMapper;
        this.appProperties = appProperties;
    }

    @Transactional
    public boolean verifyAndConsume(String targetType, String targetValue, String inputCode) {
        if (targetValue == null || targetValue.isBlank() || inputCode == null || inputCode.isBlank()) {
            return false;
        }

        var auth = appProperties.getMember().getAuth();
        if (auth.isAllowMockVerifyCode() && inputCode.equals(auth.getMockVerifyCode())) {
            return true;
        }

        String normalizedType = targetType.toLowerCase(Locale.ROOT);
        MemberVerifyCode code = memberVerifyCodeMapper.selectLatestAvailable(
                normalizedType,
                targetValue,
                LocalDateTime.now()
        );
        if (code == null) {
            return false;
        }

        if (!inputCode.equals(code.verifyCode())) {
            return false;
        }
        memberVerifyCodeMapper.markUsed(code.id());
        return true;
    }
}

