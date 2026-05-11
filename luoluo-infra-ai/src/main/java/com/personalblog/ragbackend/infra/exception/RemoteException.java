package com.personalblog.ragbackend.infra.exception;

import com.personalblog.ragbackend.infra.errorcode.BaseErrorCode;
import com.personalblog.ragbackend.infra.errorcode.IErrorCode;

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
