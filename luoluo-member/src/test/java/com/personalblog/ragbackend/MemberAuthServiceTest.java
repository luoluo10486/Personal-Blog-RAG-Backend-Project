package com.personalblog.ragbackend;

import com.personalblog.ragbackend.common.auth.dto.AuthSessionResult;
import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginRequest;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginResponse;
import com.personalblog.ragbackend.member.service.MemberAuthService;
import com.personalblog.ragbackend.member.service.MemberSessionService;
import com.personalblog.ragbackend.member.service.auth.MemberLoginStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberAuthServiceTest {
    @Mock
    private MemberLoginStrategy passwordLoginStrategy;

    @Mock
    private MemberSessionService memberSessionService;

    private MemberAuthService memberAuthService;

    @BeforeEach
    void setUp() {
        when(passwordLoginStrategy.grantType()).thenReturn("password");
        memberAuthService = new MemberAuthService(List.of(passwordLoginStrategy), memberSessionService);
    }

    @Test
    void loginShouldPassDeviceTypeAndClientIpToSessionService() {
        MemberUser user = buildUser();
        MemberLoginRequest request = new MemberLoginRequest();
        request.setGrantType(" PASSWORD ");
        request.setUsername("demo_user");
        request.setPassword("123456");
        request.setDeviceType(" web ");

        when(passwordLoginStrategy.authenticate(any(MemberLoginRequest.class))).thenReturn(user);
        when(memberSessionService.createSession(
                eq(1L),
                eq("password"),
                eq("web"),
                eq("198.51.100.10")
        )).thenReturn(new AuthSessionResult(10L, "token-123", LocalDateTime.now().plusMinutes(30)));

        MemberLoginResponse response = memberAuthService.login(request, "198.51.100.10");

        assertThat(response.token()).isEqualTo("token-123");
        assertThat(response.grantType()).isEqualTo("password");
        assertThat(response.user().username()).isEqualTo("demo_user");
        verify(memberSessionService).createSession(1L, "password", "web", "198.51.100.10");
    }

    @Test
    void loginShouldFallbackToGrantTypeWhenDeviceTypeIsBlank() {
        MemberUser user = buildUser();
        MemberLoginRequest request = new MemberLoginRequest();
        request.setGrantType("password");
        request.setUsername("demo_user");
        request.setPassword("123456");
        request.setDeviceType("   ");

        when(passwordLoginStrategy.authenticate(any(MemberLoginRequest.class))).thenReturn(user);
        when(memberSessionService.createSession(
                eq(1L),
                eq("password"),
                eq((String) null),
                eq("198.51.100.11")
        )).thenReturn(new AuthSessionResult(11L, "token-456", LocalDateTime.now().plusMinutes(30)));

        MemberLoginResponse response = memberAuthService.login(request, "198.51.100.11");

        assertThat(response.token()).isEqualTo("token-456");
        verify(memberSessionService).createSession(1L, "password", null, "198.51.100.11");
    }

    @Test
    void logoutShouldDelegateToCurrentSession() {
        doNothing().when(memberSessionService).logoutCurrentSession();

        memberAuthService.logoutCurrentSession();

        verify(memberSessionService).logoutCurrentSession();
    }

    private MemberUser buildUser() {
        MemberUser user = new MemberUser();
        user.setUserId(1L);
        user.setUsername("demo_user");
        user.setDisplayName("Demo User");
        user.setPhone("13800000000");
        user.setEmail("demo@example.com");
        user.setUserType("USER");
        return user;
    }
}
