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
public class EmailLoginStrategy implements MemberLoginStrategy {
    private final MemberUserRepository userRepository;
    private final MemberVerifyCodeService verifyCodeService;

    public EmailLoginStrategy(MemberUserRepository userRepository, MemberVerifyCodeService verifyCodeService) {
        this.userRepository = userRepository;
        this.verifyCodeService = verifyCodeService;
    }

    @Override
    public String grantType() {
        return "email";
    }

    @Override
    public MemberUser authenticate(MemberLoginRequest request) {
        String email = request.getEmail();
        String emailCode = request.getEmailCode();
        if (email == null || email.isBlank() || emailCode == null || emailCode.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "email and emailCode are required");
        }

        boolean verified = verifyCodeService.verifyAndConsume("email", email.trim(), emailCode.trim());
        if (!verified) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid email code");
        }

        return userRepository.findActiveByEmail(email.trim())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Email user not found"));
    }
}
