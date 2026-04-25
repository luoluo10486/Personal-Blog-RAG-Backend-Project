package com.personalblog.ragbackend.infra.ai.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class ProbeStreamBridge implements StreamCallback {

    private final StreamCallback downstream;
    private final CompletableFuture<ProbeResult> probe = new CompletableFuture<>();
    private final Object lock = new Object();
    private final List<Runnable> buffer = new ArrayList<>();
    private volatile boolean committed;

    ProbeStreamBridge(StreamCallback downstream) {
        this.downstream = downstream;
    }

    @Override
    public void onContent(String content) {
        probe.complete(ProbeResult.success());
        bufferOrDispatch(() -> downstream.onContent(content));
    }

    @Override
    public void onThinking(String content) {
        probe.complete(ProbeResult.success());
        bufferOrDispatch(() -> downstream.onThinking(content));
    }

    @Override
    public void onComplete() {
        probe.complete(ProbeResult.noContent());
        bufferOrDispatch(downstream::onComplete);
    }

    @Override
    public void onError(Throwable t) {
        probe.complete(ProbeResult.error(t));
        bufferOrDispatch(() -> downstream.onError(t));
    }

    ProbeResult awaitFirstPacket(long timeout, TimeUnit unit) throws InterruptedException {
        ProbeResult result;
        try {
            result = probe.get(timeout, unit);
        } catch (TimeoutException ex) {
            return ProbeResult.timeout();
        } catch (ExecutionException ex) {
            return ProbeResult.error(ex.getCause());
        }
        if (result.isSuccess()) {
            commit();
        }
        return result;
    }

    private void commit() {
        synchronized (lock) {
            if (committed) {
                return;
            }
            committed = true;
            buffer.forEach(Runnable::run);
        }
    }

    private void bufferOrDispatch(Runnable action) {
        boolean dispatchNow;
        synchronized (lock) {
            dispatchNow = committed;
            if (!dispatchNow) {
                buffer.add(action);
            }
        }
        if (dispatchNow) {
            action.run();
        }
    }

    static final class ProbeResult {
        enum Type {SUCCESS, ERROR, TIMEOUT, NO_CONTENT}

        private final Type type;
        private final Throwable error;

        private ProbeResult(Type type, Throwable error) {
            this.type = type;
            this.error = error;
        }

        static ProbeResult success() {
            return new ProbeResult(Type.SUCCESS, null);
        }

        static ProbeResult error(Throwable error) {
            return new ProbeResult(Type.ERROR, error);
        }

        static ProbeResult timeout() {
            return new ProbeResult(Type.TIMEOUT, null);
        }

        static ProbeResult noContent() {
            return new ProbeResult(Type.NO_CONTENT, null);
        }

        Type getType() {
            return type;
        }

        Throwable getError() {
            return error;
        }

        boolean isSuccess() {
            return type == Type.SUCCESS;
        }
    }
}
