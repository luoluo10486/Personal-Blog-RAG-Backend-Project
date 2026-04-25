package com.personalblog.ragbackend.infra.ai.model;

import com.personalblog.ragbackend.infra.ai.config.AIModelProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ModelHealthStore {
    private final AIModelProperties aiProperties;
    private final Map<String, ModelHealth> healthById = new ConcurrentHashMap<>();

    public ModelHealthStore(AIModelProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public boolean isUnavailable(String id) {
        ModelHealth health = healthById.get(id);
        if (health == null) {
            return false;
        }
        if (health.state == State.OPEN && health.openUntil > System.currentTimeMillis()) {
            return true;
        }
        return health.state == State.HALF_OPEN && health.halfOpenInFlight;
    }

    public boolean allowCall(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        long now = System.currentTimeMillis();
        AtomicBoolean allowed = new AtomicBoolean(false);
        healthById.compute(id, (key, current) -> {
            ModelHealth next = current == null ? new ModelHealth() : current;
            if (next.state == State.OPEN) {
                if (next.openUntil > now) {
                    return next;
                }
                next.state = State.HALF_OPEN;
                next.halfOpenInFlight = true;
                allowed.set(true);
                return next;
            }
            if (next.state == State.HALF_OPEN) {
                if (next.halfOpenInFlight) {
                    return next;
                }
                next.halfOpenInFlight = true;
                allowed.set(true);
                return next;
            }
            allowed.set(true);
            return next;
        });
        return allowed.get();
    }

    public void markSuccess(String id) {
        if (id == null || id.isBlank()) {
            return;
        }

        healthById.compute(id, (key, current) -> {
            ModelHealth next = current == null ? new ModelHealth() : current;
            next.state = State.CLOSED;
            next.consecutiveFailures = 0;
            next.openUntil = 0L;
            next.halfOpenInFlight = false;
            return next;
        });
    }

    public void markFailure(String id) {
        if (id == null || id.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        healthById.compute(id, (key, current) -> {
            ModelHealth next = current == null ? new ModelHealth() : current;
            if (next.state == State.HALF_OPEN) {
                next.state = State.OPEN;
                next.openUntil = now + aiProperties.getSelection().getOpenDurationMs();
                next.consecutiveFailures = 0;
                next.halfOpenInFlight = false;
                return next;
            }

            next.consecutiveFailures++;
            if (next.consecutiveFailures >= aiProperties.getSelection().getFailureThreshold()) {
                next.state = State.OPEN;
                next.openUntil = now + aiProperties.getSelection().getOpenDurationMs();
                next.consecutiveFailures = 0;
            }
            return next;
        });
    }

    private static class ModelHealth {
        private int consecutiveFailures;
        private long openUntil;
        private boolean halfOpenInFlight;
        private State state = State.CLOSED;
    }

    private enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}
