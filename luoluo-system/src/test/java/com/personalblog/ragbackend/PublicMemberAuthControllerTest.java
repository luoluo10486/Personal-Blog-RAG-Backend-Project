package com.personalblog.ragbackend;

import com.personalblog.ragbackend.member.dto.auth.MemberLoginResponse;
import com.personalblog.ragbackend.member.dto.auth.MemberUserSummary;
import com.personalblog.ragbackend.system.application.PublicMemberAuthApplicationService;
import com.personalblog.ragbackend.system.controller.pub.PublicMemberAuthController;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicMemberAuthControllerTest {
    @Mock
    private PublicMemberAuthApplicationService publicMemberAuthApplicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicMemberAuthController(publicMemberAuthApplicationService)).build();
    }

    @Test
    void publicLoginShouldReturnDelegatedResponse() throws Exception {
        when(publicMemberAuthApplicationService.login(any(), anyString())).thenReturn(new MemberLoginResponse(
                "token-123",
                "Bearer",
                86400,
                "password",
                new MemberUserSummary(1L, "demo_user", "Demo User", "13800000000", "demo@example.com", "USER")
        ));

        mockMvc.perform(post("/luoluo/system/public/member/auth/login")
                        .header("X-Forwarded-For", "203.0.113.8, 10.0.0.1")
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

        verify(publicMemberAuthApplicationService).login(any(), eq("203.0.113.8"));
    }
}
