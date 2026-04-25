package com.personalblog.ragbackend.infra.ai.model;

import com.personalblog.ragbackend.infra.ai.errorcode.BaseErrorCode;
import com.personalblog.ragbackend.infra.ai.exception.RemoteException;
import com.personalblog.ragbackend.infra.ai.enums.ModelCapability;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
public class ModelRoutingExecutor {
    private final ModelHealthStore modelHealthStore;

    public ModelRoutingExecutor(ModelHealthStore modelHealthStore) {
        this.modelHealthStore = modelHealthStore;
    }

    public <C, T> T executeWithFallback(ModelCapability capability,
                                        List<ModelTarget> targets,
                                        Function<ModelTarget, C> clientResolver,
                                        ModelCaller<C, T> caller) {
        if (targets == null || targets.isEmpty()) {
            throw new RemoteException("No " + capability.getDisplayName() + " model candidates available");
        }

        Throwable lastError = null;
        for (ModelTarget target : targets) {
            C client = clientResolver.apply(target);
            if (client == null || !modelHealthStore.allowCall(target.id())) {
                continue;
            }

            try {
                T result = caller.call(client, target);
                modelHealthStore.markSuccess(target.id());
                return result;
            } catch (Exception exception) {
                lastError = exception;
                modelHealthStore.markFailure(target.id());
            }
        }

        throw new RemoteException(
                "All " + capability.getDisplayName() + " model candidates failed: "
                        + (lastError == null ? "unknown" : lastError.getMessage()),
                lastError,
                BaseErrorCode.REMOTE_ERROR
        );
    }
}
