package com.personalblog.ragbackend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MemberAuthControllerTest 测试类，用于验证相关功能行为。
 */
@SpringBootTest(classes = LuoluoMemberTestApplication.class)
@AutoConfigureMockMvc
class MemberAuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void passwordLoginShouldReturnToken() throws Exception {
        String response = mockMvc.perform(post("/luoluo/member/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "grantType": "password",
                                  "username": "demo_user",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("登录成功"))
                .andExpect(jsonPath("$.data.grantType").value("password"))
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        String token = json.get("data").get("token").asText();
        assertThat(token).isNotBlank();
    }

    @Test
    void profileShouldReturnCurrentUserWhenTokenValid() throws Exception {
        String loginResponse = mockMvc.perform(post("/luoluo/member/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "grantType": "password",
                                  "username": "demo_user",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(loginResponse).get("data").get("token").asText();

        mockMvc.perform(get("/luoluo/member/profile/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("查询成功"))
                .andExpect(jsonPath("$.data.username").value("demo_user"));
    }

    @Test
    void sendSmsCodeShouldReturnIssuedCodeWhenUsingMockSender() throws Exception {
        mockMvc.perform(post("/luoluo/member/auth/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "grantType": "sms",
                                  "phone": "13800138000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("验证码发送成功"))
                .andExpect(jsonPath("$.data.grantType").value("sms"))
                .andExpect(jsonPath("$.data.target").value("138****8000"))
                .andExpect(jsonPath("$.data.issuedCode").isString());
    }

    @Test
    void sendEmailCodeShouldReturnIssuedCodeWhenUsingMockSender() throws Exception {
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
                .andExpect(jsonPath("$.message").value("验证码发送成功"))
                .andExpect(jsonPath("$.data.grantType").value("email"))
                .andExpect(jsonPath("$.data.target").value("d***@example.com"))
                .andExpect(jsonPath("$.data.issuedCode").isString());
    }
}

