package com.personalblog.ragbackend;

import com.personalblog.ragbackend.member.application.MemberAuthApplicationService;
import com.personalblog.ragbackend.member.controller.MemberAuthController;
import com.personalblog.ragbackend.member.dto.auth.MemberLoginResponse;
import com.personalblog.ragbackend.member.dto.auth.MemberUserSummary;
import com.personalblog.ragbackend.member.dto.code.MemberSendVerifyCodeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MemberAuthControllerTest {
    @Mock
    private MemberAuthApplicationService memberAuthApplicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MemberAuthController(memberAuthApplicationService)).build();
    }

    @Test
    void loginShouldReturnDelegatedResponse() throws Exception {
        when(memberAuthApplicationService.login(any(), anyString())).thenReturn(new MemberLoginResponse(
                "token-123",
                "Bearer",
                86400,
                "password",
                new MemberUserSummary(1L, "demo_user", "Demo User", "13800000000", "demo@example.com", "USER")
        ));

        mockMvc.perform(post("/luoluo/member/auth/login")
                        .header("X-Forwarded-For", "198.51.100.10, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "grantType": "password",
                                  "deviceType": "web",
                                  "email": "demo@example.com",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("token-123"))
                .andExpect(jsonPath("$.data.user.email").value("demo@example.com"));

        verify(memberAuthApplicationService).login(any(), eq("198.51.100.10"));
    }

    @Test
    void logoutShouldReturnSuccess() throws Exception {
        doNothing().when(memberAuthApplicationService).logout();

        mockMvc.perform(post("/luoluo/member/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("退出成功"));

        verify(memberAuthApplicationService).logout();
    }

    @Test
    void sendCodeShouldReturnDelegatedResponse() throws Exception {
        when(memberAuthApplicationService.sendCode(any())).thenReturn(new MemberSendVerifyCodeResponse(
                "req-1",
                "email",
                "d***@example.com",
                120,
                "123456"
        ));

        mockMvc.perform(post("/luoluo/member/auth/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "grantType": "email",
                                  "email": "demo@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.requestId").value("req-1"))
                .andExpect(jsonPath("$.data.grantType").value("email"));
    }
}
