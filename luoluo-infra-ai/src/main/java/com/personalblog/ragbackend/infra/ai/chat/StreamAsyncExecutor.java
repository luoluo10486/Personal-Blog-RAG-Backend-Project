package com.personalblog.ragbackend.infra.ai.chat;

import com.personalblog.ragbackend.infra.ai.http.ModelClientErrorType;
import com.personalblog.ragbackend.infra.ai.http.ModelClientException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class StreamAsyncExecutor {

    private static final String STREAM_BUSY_MESSAGE = "流式线程池繁忙";

    private StreamAsyncExecutor() {
    }

    static StreamCancellationHandle submit(Executor executor,
                                           StreamCallback callback,
                                           Consumer<AtomicBoolean> streamTask) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        try {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> streamTask.accept(cancelled), executor);
            return StreamCancellationHandles.fromFuture(future, cancelled);
        } catch (RejectedExecutionException ex) {
            callback.onError(new ModelClientException(STREAM_BUSY_MESSAGE, ModelClientErrorType.SERVER_ERROR, null, ex));
            return StreamCancellationHandles.noop();
        }
    }
}
