package com.personalblog.ragbackend.infra.token;

public interface TokenCounterService {

    Integer countTokens(String text);
}
