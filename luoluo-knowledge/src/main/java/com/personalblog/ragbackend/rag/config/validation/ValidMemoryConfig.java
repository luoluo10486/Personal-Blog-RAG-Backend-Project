package com.personalblog.ragbackend.rag.config.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MemoryConfigValidator.class)
@Documented
public @interface ValidMemoryConfig {

    String message() default "Memory configuration is invalid";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
