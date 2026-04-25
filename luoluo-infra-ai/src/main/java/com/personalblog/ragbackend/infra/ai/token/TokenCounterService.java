package com.personalblog.ragbackend.infra.ai.token;

public interface TokenCounterService {

    Integer countTokens(String text);
}
