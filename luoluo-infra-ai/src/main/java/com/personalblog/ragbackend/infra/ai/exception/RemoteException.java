package com.personalblog.ragbackend.infra.ai.exception;

import com.personalblog.ragbackend.infra.ai.errorcode.BaseErrorCode;
import com.personalblog.ragbackend.infra.ai.errorcode.IErrorCode;

public class RemoteException extends AbstractException {

    public RemoteException(String message) {
        this(message, null, BaseErrorCode.REMOTE_ERROR);
    }

    public RemoteException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public RemoteException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }
}
