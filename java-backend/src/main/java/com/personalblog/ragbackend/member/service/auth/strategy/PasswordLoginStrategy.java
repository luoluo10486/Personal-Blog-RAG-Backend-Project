package com.personalblog.ragbackend.member.service.auth.strategy;

import com.personalblog.ragbackend.config.AppProperties;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.model.MemberUser;
import com.personalblog.ragbackend.member.repository.MemberUserRepository;
import com.personalblog.ragbackend.member.service.auth.MemberLoginStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class PasswordLoginStrategy implements MemberLoginStrategy {
    private final MemberUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    public PasswordLoginStrategy(
            MemberUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AppProperties appProperties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
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
            throw new ResponseStatusException(BAD_REQUEST, "username and password are required");
        }

        MemberUser user = userRepository.findActiveByUsername(username.trim())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid username or password"));

        boolean matched = matchesPassword(password, user.passwordHash());
        if (!matched) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid username or password");
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
            // Fall back to plain comparison only in local/dev-compatible mode.
        }
        return appProperties.getMember().getAuth().isAllowPlainPassword() && raw.equals(storedHash);
    }
}
