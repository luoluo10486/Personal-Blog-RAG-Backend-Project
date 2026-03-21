package com.personalblog.ragbackend.common.satoken.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * SaTokenExceptionHandler 处理器类，用于统一处理框架扩展逻辑。
 */
@RestControllerAdvice
public class SaTokenExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleNotLogin(NotLoginException exception) {
        return Map.of(
                "code", HttpStatus.UNAUTHORIZED.value(),
                "message", "未授权：" + exception.getMessage()
        );
    }

    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleNotPermission(NotPermissionException exception) {
        return Map.of(
                "code", HttpStatus.FORBIDDEN.value(),
                "message", "禁止访问：" + exception.getMessage()
        );
    }

    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleNotRole(NotRoleException exception) {
        return Map.of(
                "code", HttpStatus.FORBIDDEN.value(),
                "message", "禁止访问：" + exception.getMessage()
        );
    }
}

