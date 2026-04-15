package com.personalblog.ragbackend;

import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginResponse;
import com.personalblog.ragbackend.member.dto.auth.MemberRegisterRequest;
import com.personalblog.ragbackend.member.dto.auth.MemberUserSummary;
import com.personalblog.ragbackend.member.service.MemberAuthService;
import com.personalblog.ragbackend.member.service.MemberRegisterService;
import com.personalblog.ragbackend.member.service.MemberUserService;
import com.personalblog.ragbackend.member.service.MemberVerifyCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberRegisterServiceTest {
    @Mock
    private MemberUserService memberUserService;

    @Mock
    private MemberVerifyCodeService memberVerifyCodeService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MemberAuthService memberAuthService;

    private MemberRegisterService memberRegisterService;

    @BeforeEach
    void setUp() {
        memberRegisterService = new MemberRegisterService(
                memberUserService,
                memberVerifyCodeService,
                passwordEncoder,
                memberAuthService
        );
    }

    @Test
    void emailRegisterShouldCreateUserAndLogin() {
        MemberRegisterRequest request = new MemberRegisterRequest();
        request.setGrantType(" email ");
        request.setEmail("New@Example.com");
        request.setEmailCode("123456");
        request.setPassword("123456");
        request.setConfirmPassword("123456");
        request.setDisplayName("New User");
        request.setDeviceType("web");

        when(memberVerifyCodeService.verifyRegisterOrLoginCodeAndConsume("email", "new@example.com", "123456"))
                .thenReturn(true);
        when(memberUserService.existsByUsername("new@example.com")).thenReturn(false);
        when(memberUserService.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");
        when(memberUserService.create(any(MemberUser.class))).thenAnswer(invocation -> {
            MemberUser user = invocation.getArgument(0);
            user.setUserId(9L);
            return user;
        });
        when(memberAuthService.createLoginResponse(any(MemberUser.class), eq("email"), eq("web"), eq("198.51.100.20")))
                .thenReturn(new MemberLoginResponse(
                        "token-123",
                        "Bearer",
                        86400,
                        "email",
                        new MemberUserSummary(9L, "new@example.com", "New User", null, "new@example.com", "USER")
                ));

        MemberLoginResponse response = memberRegisterService.register(request, "198.51.100.20");

        ArgumentCaptor<MemberUser> userCaptor = ArgumentCaptor.forClass(MemberUser.class);
        verify(memberUserService).create(userCaptor.capture());
        MemberUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("new@example.com");
        assertThat(savedUser.getEmail()).isEqualTo("new@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getDisplayName()).isEqualTo("New User");
        assertThat(savedUser.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.token()).isEqualTo("token-123");
    }

    @Test
    void duplicateUsernameShouldBeRejected() {
        MemberRegisterRequest request = new MemberRegisterRequest();
        request.setGrantType("password");
        request.setUsername("demo_user");
        request.setPassword("123456");

        when(memberUserService.existsByUsername("demo_user")).thenReturn(true);

        assertThatThrownBy(() -> memberRegisterService.register(request, "198.51.100.21"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode().value()).isEqualTo(409));
    }
}
