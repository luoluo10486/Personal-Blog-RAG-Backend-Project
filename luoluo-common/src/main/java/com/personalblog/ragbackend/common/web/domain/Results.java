package com.personalblog.ragbackend.common.web.domain;

public final class Results {

    private Results() {
    }

    public static Result<Void> success() {
        return new Result<Void>().setCode(Result.SUCCESS_CODE);
    }

    public static <T> Result<T> success(T data) {
        return new Result<T>().setCode(Result.SUCCESS_CODE).setData(data);
    }

    public static Result<Void> failure() {
        return new Result<Void>().setCode("500").setMessage("服务器内部错误");
    }

    public static Result<Void> failure(String errorCode, String errorMessage) {
        return new Result<Void>().setCode(errorCode).setMessage(errorMessage);
    }
}
