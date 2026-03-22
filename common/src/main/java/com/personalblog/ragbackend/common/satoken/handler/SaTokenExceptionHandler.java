package com.personalblog.ragbackend.common.satoken.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.personalblog.ragbackend.common.web.constant.ResultCode;
import com.personalblog.ragbackend.common.web.domain.R;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Sa-Token 异常处理器，统一转换鉴权相关异常响应。
 */
@RestControllerAdvice
public class SaTokenExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<R<Void>> handleNotLogin(NotLoginException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                R.fail(ResultCode.UNAUTHORIZED, "未授权：" + exception.getMessage())
        );
    }

    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<R<Void>> handleNotPermission(NotPermissionException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                R.fail(ResultCode.FORBIDDEN, "禁止访问：" + exception.getMessage())
        );
    }

    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<R<Void>> handleNotRole(NotRoleException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                R.fail(ResultCode.FORBIDDEN, "禁止访问：" + exception.getMessage())
        );
    }
}
