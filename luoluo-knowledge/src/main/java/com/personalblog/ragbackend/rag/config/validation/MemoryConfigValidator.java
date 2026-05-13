package com.personalblog.ragbackend.rag.config.validation;

import com.personalblog.ragbackend.rag.config.MemoryProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MemoryConfigValidator implements ConstraintValidator<ValidMemoryConfig, MemoryProperties> {

    @Override
    public boolean isValid(MemoryProperties config, ConstraintValidatorContext context) {
        if (config == null) {
            return true;
        }

        if (Boolean.TRUE.equals(config.getSummaryEnabled())) {
            Integer summaryStartTurns = config.getSummaryStartTurns();
            Integer historyKeepTurns = config.getHistoryKeepTurns();
            if (summaryStartTurns != null && historyKeepTurns != null && summaryStartTurns <= historyKeepTurns) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                        "summaryStartTurns must be greater than historyKeepTurns when summary is enabled"
                ).addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
