package com.personalblog.ragbackend.member.service.auth.strategy;

import com.personalblog.ragbackend.member.config.MemberProperties;
import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.service.MemberUserService;
import com.personalblog.ragbackend.member.service.auth.MemberLoginStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class PasswordLoginStrategy implements MemberLoginStrategy {
    private final MemberUserService memberUserService;
    private final PasswordEncoder passwordEncoder;
    private final MemberProperties memberProperties;

    public PasswordLoginStrategy(
            MemberUserService memberUserService,
            PasswordEncoder passwordEncoder,
            MemberProperties memberProperties
    ) {
        this.memberUserService = memberUserService;
        this.passwordEncoder = passwordEncoder;
        this.memberProperties = memberProperties;
    }

    @Override
    public String grantType() {
        return "password";
    }

    @Override
    public MemberUser authenticate(MemberLoginRequest request) {
        String account = resolvePasswordAccount(request);
        String password = request.getPassword();
        if (account == null || account.isBlank() || password == null || password.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "username/email/phone and password must not be blank");
        }

        MemberUser user = memberUserService.findActiveByPasswordAccount(account);
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "account or password is invalid");
        }

        boolean matched = matchesPassword(password, user.getPasswordHash());
        if (!matched) {
            throw new ResponseStatusException(UNAUTHORIZED, "account or password is invalid");
        }
        return user;
    }

    private String resolvePasswordAccount(MemberLoginRequest request) {
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            return request.getUsername().trim();
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            return request.getEmail().trim();
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            return request.getPhone().trim();
        }
        return null;
    }

    private boolean matchesPassword(String raw, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            if (passwordEncoder.matches(raw, storedHash)) {
                return true;
            }
        } catch (IllegalArgumentException ignored) {
            // Keep compatibility with local demo plaintext passwords.
        }
        return memberProperties.getMember().getAuth().isAllowPlainPassword() && raw.equals(storedHash);
    }
}
