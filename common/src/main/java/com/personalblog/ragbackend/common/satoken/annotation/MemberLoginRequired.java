package com.personalblog.ragbackend.common.satoken.annotation;

import cn.dev33.satoken.annotation.SaCheckLogin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * MemberLoginRequired 注解定义。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@SaCheckLogin
public @interface MemberLoginRequired {
}

