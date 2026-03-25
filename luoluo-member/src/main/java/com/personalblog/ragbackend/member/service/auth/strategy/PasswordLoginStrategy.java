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
        String username = request.getUsername();
        String password = request.getPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "username and password must not be blank");
        }

        MemberUser user = memberUserService.findActiveByUsername(username.trim());
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "username or password is invalid");
        }

        boolean matched = matchesPassword(password, user.getPasswordHash());
        if (!matched) {
            throw new ResponseStatusException(UNAUTHORIZED, "username or password is invalid");
        }
        return user;
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
