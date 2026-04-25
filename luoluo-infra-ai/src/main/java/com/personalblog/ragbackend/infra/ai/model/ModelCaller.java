package com.personalblog.ragbackend.infra.ai.model;

@FunctionalInterface
public interface ModelCaller<C, T> {

    T call(C client, ModelTarget target) throws Exception;
}
