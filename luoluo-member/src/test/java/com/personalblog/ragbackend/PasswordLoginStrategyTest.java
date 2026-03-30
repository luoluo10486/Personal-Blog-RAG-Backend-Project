package com.personalblog.ragbackend;

import com.personalblog.ragbackend.member.config.MemberProperties;
import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.service.MemberUserService;
import com.personalblog.ragbackend.member.service.auth.strategy.PasswordLoginStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordLoginStrategyTest {
    @Mock
    private MemberUserService memberUserService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PasswordLoginStrategy strategy;

    @BeforeEach
    void setUp() {
        MemberProperties memberProperties = new MemberProperties();
        memberProperties.getMember().getAuth().setAllowPlainPassword(true);
        strategy = new PasswordLoginStrategy(memberUserService, passwordEncoder, memberProperties);
    }

    @Test
    void shouldAuthenticateWithUsername() {
        MemberUser user = buildUser();
        when(memberUserService.findActiveByPasswordAccount("demo_user")).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        MemberLoginRequest request = new MemberLoginRequest();
        request.setGrantType("password");
        request.setUsername("demo_user");
        request.setPassword("123456");

        MemberUser authenticated = strategy.authenticate(request);

        assertThat(authenticated).isSameAs(user);
        verify(memberUserService).findActiveByPasswordAccount("demo_user");
    }

    @Test
    void shouldAuthenticateWithEmail() {
        MemberUser user = buildUser();
        when(memberUserService.findActiveByPasswordAccount("demo@example.com")).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        MemberLoginRequest request = new MemberLoginRequest();
        request.setGrantType("password");
        request.setEmail("demo@example.com");
        request.setPassword("123456");

        MemberUser authenticated = strategy.authenticate(request);

        assertThat(authenticated).isSameAs(user);
        verify(memberUserService).findActiveByPasswordAccount("demo@example.com");
    }

    @Test
    void shouldAuthenticateWithPhone() {
        MemberUser user = buildUser();
        when(memberUserService.findActiveByPasswordAccount("13800000000")).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        MemberLoginRequest request = new MemberLoginRequest();
        request.setGrantType("password");
        request.setPhone("13800000000");
        request.setPassword("123456");

        MemberUser authenticated = strategy.authenticate(request);

        assertThat(authenticated).isSameAs(user);
        verify(memberUserService).findActiveByPasswordAccount("13800000000");
    }

    private MemberUser buildUser() {
        MemberUser user = new MemberUser();
        user.setUserId(1L);
        user.setUsername("demo_user");
        user.setEmail("demo@example.com");
        user.setPhone("13800000000");
        user.setPasswordHash("$2a$10$mock-hash");
        return user;
    }
}
