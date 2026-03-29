package com.personalblog.ragbackend.common.mail.service;

import com.personalblog.ragbackend.common.mail.dto.MailSendReceipt;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

/**
 * 通用邮件发送服务。
 * 当未配置真实邮件客户端时，会自动退化为 mock 回执，方便本地联调。
 */
@Service
public class CommonMailSender {
    private static final String MOCK_PROVIDER = "common-mock-mail";
    private static final String SPRING_MAIL_PROVIDER = "spring-mail";

    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
    private final Environment environment;

    public CommonMailSender(ObjectProvider<JavaMailSender> javaMailSenderProvider, Environment environment) {
        this.javaMailSenderProvider = javaMailSenderProvider;
        this.environment = environment;
    }

    /**
     * 发送纯文本邮件。
     *
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @return 邮件发送回执
     */
    public MailSendReceipt sendText(String to, String subject, String content) {
        if (!StringUtils.hasText(to)) {
            throw new IllegalArgumentException("mail recipient must not be blank");
        }
        if (!StringUtils.hasText(subject)) {
            throw new IllegalArgumentException("mail subject must not be blank");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("mail content must not be blank");
        }

        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        if (javaMailSender == null || !StringUtils.hasText(resolveHost())) {
            return new MailSendReceipt(MOCK_PROVIDER, randomRequestId(), true);
        }

        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setTo(to.trim());
            String fromAddress = resolveFromAddress();
            if (StringUtils.hasText(fromAddress)) {
                helper.setFrom(fromAddress);
            }
            helper.setSubject(subject.trim());
            helper.setText(content, false);
            javaMailSender.send(mimeMessage);
            return new MailSendReceipt(resolveProvider(), resolveRequestId(mimeMessage), false);
        } catch (MailException | MessagingException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Email send failed", ex);
        }
    }

    /**
     * 解析当前实际使用的邮件提供方标识。
     */
    private String resolveProvider() {
        String host = resolveHost();
        return StringUtils.hasText(host) ? SPRING_MAIL_PROVIDER + ":" + host.trim() : SPRING_MAIL_PROVIDER;
    }

    /**
     * 读取邮件服务器地址。
     */
    private String resolveHost() {
        return environment.getProperty("spring.mail.host");
    }

    /**
     * 读取发件人地址，优先使用用户名配置。
     */
    private String resolveFromAddress() {
        String username = environment.getProperty("spring.mail.username");
        if (StringUtils.hasText(username)) {
            return username.trim();
        }
        return environment.getProperty("spring.mail.properties.mail.smtp.from");
    }

    /**
     * 从 MimeMessage 中提取请求标识，不存在时生成兜底值。
     */
    private String resolveRequestId(MimeMessage mimeMessage) throws MessagingException {
        String messageId = mimeMessage.getMessageID();
        return StringUtils.hasText(messageId) ? messageId : randomRequestId();
    }

    /**
     * 生成随机请求标识。
     */
    private String randomRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
