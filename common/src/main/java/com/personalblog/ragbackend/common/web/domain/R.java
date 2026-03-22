package com.personalblog.ragbackend.common.web.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.personalblog.ragbackend.common.web.constant.ResultCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一接口响应对象，返回 `code`、`message`、`data` 三段结构。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public static <T> R<T> ok() { return build(ResultCode.SUCCESS, "操作成功", null); }
    public static <T> R<T> ok(T data) { return build(ResultCode.SUCCESS, "操作成功", data); }
    public static <T> R<T> ok(String message) { return build(ResultCode.SUCCESS, message, null); }
    public static <T> R<T> ok(String message, T data) { return build(ResultCode.SUCCESS, message, data); }
    public static <T> R<T> fail(String message) { return build(ResultCode.FAIL, message, null); }
    public static <T> R<T> fail(int code, String message) { return build(code, message, null); }
    public static <T> R<T> fail(int code, String message, T data) { return build(code, message, data); }

    private static <T> R<T> build(int code, String message, T data) {
        R<T> response = new R<>();
        response.setCode(code);
        response.setMessage(message);
        response.setData(data);
        return response;
    }
}
