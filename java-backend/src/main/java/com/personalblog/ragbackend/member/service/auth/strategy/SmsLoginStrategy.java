package com.personalblog.ragbackend.member.service.auth.strategy;

import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.model.MemberUser;
import com.personalblog.ragbackend.member.repository.MemberUserRepository;
import com.personalblog.ragbackend.member.service.MemberVerifyCodeService;
import com.personalblog.ragbackend.member.service.auth.MemberLoginStrategy;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class SmsLoginStrategy implements MemberLoginStrategy {
    private final MemberUserRepository userRepository;
    private final MemberVerifyCodeService verifyCodeService;

    public SmsLoginStrategy(MemberUserRepository userRepository, MemberVerifyCodeService verifyCodeService) {
        this.userRepository = userRepository;
        this.verifyCodeService = verifyCodeService;
    }

    @Override
    public String grantType() {
        return "sms";
    }

    @Override
    public MemberUser authenticate(MemberLoginRequest request) {
        String phone = request.getPhone();
        String smsCode = request.getSmsCode();
        if (phone == null || phone.isBlank() || smsCode == null || smsCode.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "phone and smsCode are required");
        }

        boolean verified = verifyCodeService.verifyAndConsume("sms", phone.trim(), smsCode.trim());
        if (!verified) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid sms code");
        }

        return userRepository.findActiveByPhone(phone.trim())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Phone user not found"));
    }
}
