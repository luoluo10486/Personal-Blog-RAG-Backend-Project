package com.personalblog.ragbackend.infra.ai.exception;

import com.personalblog.ragbackend.infra.ai.errorcode.IErrorCode;

public abstract class AbstractException extends RuntimeException {

    protected final IErrorCode errorCode;
    protected final String errorMessage;

    protected AbstractException(String message, Throwable cause, IErrorCode errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorMessage = message;
    }

    public IErrorCode getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
