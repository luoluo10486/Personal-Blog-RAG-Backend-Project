package com.personalblog.ragbackend.member.service;

import com.personalblog.ragbackend.config.AppProperties;
import com.personalblog.ragbackend.member.model.MemberVerifyCode;
import com.personalblog.ragbackend.member.repository.MemberVerifyCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
public class MemberVerifyCodeService {
    private final MemberVerifyCodeRepository codeRepository;
    private final AppProperties appProperties;

    public MemberVerifyCodeService(MemberVerifyCodeRepository codeRepository, AppProperties appProperties) {
        this.codeRepository = codeRepository;
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
        Optional<MemberVerifyCode> latest = codeRepository.findLatestAvailable(
                normalizedType,
                targetValue,
                LocalDateTime.now()
        );
        if (latest.isEmpty()) {
            return false;
        }

        MemberVerifyCode code = latest.get();
        if (!inputCode.equals(code.verifyCode())) {
            return false;
        }
        codeRepository.markUsed(code.id());
        return true;
    }
}
