package com.personalblog.ragbackend.infra.ai.http;

public enum ModelClientErrorType {
    UNAUTHORIZED,
    RATE_LIMITED,
    SERVER_ERROR,
    CLIENT_ERROR,
    NETWORK_ERROR,
    INVALID_RESPONSE,
    PROVIDER_ERROR;

    public static ModelClientErrorType fromHttpStatus(int status) {
        if (status == 401 || status == 403) {
            return UNAUTHORIZED;
        }
        if (status == 429) {
            return RATE_LIMITED;
        }
        if (status >= 500) {
            return SERVER_ERROR;
        }
        return CLIENT_ERROR;
    }
}
