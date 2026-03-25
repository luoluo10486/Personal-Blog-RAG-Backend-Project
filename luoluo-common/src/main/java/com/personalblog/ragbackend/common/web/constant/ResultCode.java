package com.personalblog.ragbackend.common.web.constant;

/**
 * 统一接口返回码定义。
 */
public final class ResultCode {
    public static final int SUCCESS = 0;
    public static final int FAIL = 1;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;

    private ResultCode() {
    }
}
