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

    private String resolveProvider() {
        String host = resolveHost();
        return StringUtils.hasText(host) ? SPRING_MAIL_PROVIDER + ":" + host.trim() : SPRING_MAIL_PROVIDER;
    }

    private String resolveHost() {
        return environment.getProperty("spring.mail.host");
    }

    private String resolveFromAddress() {
        String username = environment.getProperty("spring.mail.username");
        if (StringUtils.hasText(username)) {
            return username.trim();
        }
        return environment.getProperty("spring.mail.properties.mail.smtp.from");
    }

    private String resolveRequestId(MimeMessage mimeMessage) throws MessagingException {
        String messageId = mimeMessage.getMessageID();
        return StringUtils.hasText(messageId) ? messageId : randomRequestId();
    }

    private String randomRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
