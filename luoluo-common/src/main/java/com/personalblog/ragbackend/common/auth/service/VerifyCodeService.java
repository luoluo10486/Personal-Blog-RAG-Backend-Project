package com.personalblog.ragbackend.common.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.common.auth.domain.VerifyCodeRecord;
import com.personalblog.ragbackend.common.auth.dto.VerifyCodeIssueCommand;
import com.personalblog.ragbackend.common.auth.dto.VerifyCodeVerifyCommand;
import com.personalblog.ragbackend.common.auth.mapper.VerifyCodeRecordMapper;
import com.personalblog.ragbackend.common.redis.RedisClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 通用验证码服务，统一处理验证码缓存、留痕和核销。
 */
@Service
public class VerifyCodeService {
    private static final String VERIFY_CODE_KEY_PREFIX = "auth:verify_code:";

    private final VerifyCodeRecordMapper verifyCodeRecordMapper;
    private final RedisClient redisClient;

    public VerifyCodeService(
            VerifyCodeRecordMapper verifyCodeRecordMapper,
            RedisClient redisClient
    ) {
        this.verifyCodeRecordMapper = verifyCodeRecordMapper;
        this.redisClient = redisClient;
    }

    @Transactional
    public boolean verifyAndConsume(VerifyCodeVerifyCommand command) {
        if (command.namespace() == null || command.namespace().isBlank()
                || command.bizType() == null || command.bizType().isBlank()
                || command.targetType() == null || command.targetType().isBlank()) {
            return false;
        }
        if (command.targetValue() == null || command.targetValue().isBlank()
                || command.inputCode() == null || command.inputCode().isBlank()) {
            return false;
        }
        if (command.allowMockCode() && command.inputCode().equals(command.mockCode())) {
            return true;
        }

        String normalizedBizType = normalizeUpper(command.bizType());
        String normalizedTargetType = normalizeLower(command.targetType());
        String normalizedTargetValue = normalizeTargetValue(normalizedTargetType, command.targetValue());
        String inputCodeValue = command.inputCode().trim();
        String cacheKey = buildCacheKey(command.namespace(), normalizedBizType, normalizedTargetType, normalizedTargetValue);
        if (!redisClient.compareAndDelete(cacheKey, inputCodeValue)) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        int updatedRows = verifyCodeRecordMapper.update(null, Wrappers.<VerifyCodeRecord>lambdaUpdate()
                .eq(VerifyCodeRecord::getBizType, normalizedBizType)
                .eq(VerifyCodeRecord::getTargetType, normalizedTargetType)
                .eq(VerifyCodeRecord::getTargetValue, normalizedTargetValue)
                .eq(VerifyCodeRecord::getCodeValue, inputCodeValue)
                .eq(VerifyCodeRecord::getUsed, Boolean.FALSE)
                .gt(VerifyCodeRecord::getExpiresAt, now)
                .set(VerifyCodeRecord::getUsed, Boolean.TRUE)
                .set(VerifyCodeRecord::getUsedAt, now));
        if (updatedRows != 1) {
            throw new IllegalStateException("Verify code consume update expected 1 row but got " + updatedRows);
        }
        return true;
    }

    @Transactional
    public void issue(VerifyCodeIssueCommand command) {
        if (command.namespace() == null || command.namespace().isBlank()) {
            throw new IllegalArgumentException("验证码命名空间不能为空");
        }
        if (command.bizType() == null || command.bizType().isBlank()) {
            throw new IllegalArgumentException("业务类型不能为空");
        }
        if (command.targetType() == null || command.targetType().isBlank()) {
            throw new IllegalArgumentException("目标类型不能为空");
        }
        if (command.targetValue() == null || command.targetValue().isBlank()) {
            throw new IllegalArgumentException("目标值不能为空");
        }
        if (command.verifyCode() == null || command.verifyCode().isBlank()) {
            throw new IllegalArgumentException("验证码不能为空");
        }
        if (command.ttlSeconds() <= 0) {
            throw new IllegalArgumentException("验证码有效期必须大于 0");
        }

        String normalizedBizType = normalizeUpper(command.bizType());
        String normalizedTargetType = normalizeLower(command.targetType());
        String normalizedTargetValue = normalizeTargetValue(normalizedTargetType, command.targetValue());
        String verifyCodeValue = command.verifyCode().trim();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(command.ttlSeconds());

        redisClient.set(
                buildCacheKey(command.namespace(), normalizedBizType, normalizedTargetType, normalizedTargetValue),
                verifyCodeValue,
                Duration.ofSeconds(command.ttlSeconds())
        );

        VerifyCodeRecord record = new VerifyCodeRecord();
        record.setBizType(normalizedBizType);
        record.setBizId(normalizeNullable(command.bizId(), false));
        record.setSubjectType(normalizeNullable(command.subjectType(), true));
        record.setSubjectId(command.subjectId());
        record.setTargetType(normalizedTargetType);
        record.setTargetValue(normalizedTargetValue);
        record.setChannel(normalizeNullable(command.channel(), true));
        record.setTemplateId(normalizeNullable(command.templateId(), false));
        record.setProvider(normalizeNullable(command.provider(), false));
        record.setRequestId(normalizeNullable(command.requestId(), false));
        record.setCodeValue(verifyCodeValue);
        record.setExpiresAt(expiresAt);
        record.setUsed(Boolean.FALSE);
        record.setDeleted(0);
        record.setCreatedAt(now);
        record.setRemark(normalizeNullable(command.remark(), false));
        verifyCodeRecordMapper.insert(record);
    }

    private String buildCacheKey(String namespace, String bizType, String targetType, String targetValue) {
        return VERIFY_CODE_KEY_PREFIX
                + normalizeLower(namespace)
                + ":" + bizType
                + ":" + targetType
                + ":" + targetValue;
    }

    private String normalizeTargetValue(String targetType, String targetValue) {
        String value = targetValue.trim();
        if ("email".equals(targetType)) {
            return value.toLowerCase(Locale.ROOT);
        }
        return value;
    }

    private String normalizeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value, boolean upperCase) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return upperCase ? value.trim().toUpperCase(Locale.ROOT) : value.trim();
    }
}
