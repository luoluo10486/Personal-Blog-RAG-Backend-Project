package com.personalblog.ragbackend.infra.ai.chat;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StreamCancellationHandles {

    private static final StreamCancellationHandle NOOP = () -> {
    };

    private StreamCancellationHandles() {
    }

    public static StreamCancellationHandle noop() {
        return NOOP;
    }

    public static StreamCancellationHandle fromFuture(Future<?> future, AtomicBoolean cancelled) {
        return new FutureCancellationHandle(future, cancelled);
    }

    private static final class FutureCancellationHandle implements StreamCancellationHandle {
        private final Future<?> future;
        private final AtomicBoolean cancelled;
        private final AtomicBoolean once = new AtomicBoolean(false);

        private FutureCancellationHandle(Future<?> future, AtomicBoolean cancelled) {
            this.future = future;
            this.cancelled = cancelled;
        }

        @Override
        public void cancel() {
            if (!once.compareAndSet(false, true)) {
                return;
            }
            if (cancelled != null) {
                cancelled.set(true);
            }
            if (future != null) {
                future.cancel(true);
            }
        }
    }
}
