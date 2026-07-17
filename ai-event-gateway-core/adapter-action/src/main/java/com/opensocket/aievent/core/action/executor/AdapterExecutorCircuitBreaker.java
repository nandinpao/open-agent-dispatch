package com.opensocket.aievent.core.action.executor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class AdapterExecutorCircuitBreaker {
    private final AdapterActionExecutionProperties properties;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public AdapterExecutorCircuitBreaker(AdapterActionExecutionProperties properties) {
        this.properties = properties;
    }

    public boolean isOpen(String executorName) {
        if (!properties.getCircuitBreaker().isEnabled() || executorName == null || executorName.isBlank()) {
            return false;
        }
        State state = states.get(executorName);
        if (state == null || state.openUntil == null) return false;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!state.openUntil.isAfter(now)) {
            states.remove(executorName);
            return false;
        }
        return true;
    }

    public OffsetDateTime openUntil(String executorName) {
        State state = states.get(executorName);
        return state == null ? null : state.openUntil;
    }

    public void recordSuccess(String executorName) {
        if (executorName != null) states.remove(executorName);
    }

    public void recordFailure(String executorName) {
        if (!properties.getCircuitBreaker().isEnabled() || executorName == null || executorName.isBlank()) return;
        State state = states.computeIfAbsent(executorName, k -> new State());
        state.failureCount++;
        if (state.failureCount >= properties.getCircuitBreaker().getFailureThreshold()) {
            state.openUntil = OffsetDateTime.now(ZoneOffset.UTC).plus(properties.getCircuitBreaker().getOpenDuration());
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        states.forEach((name, state) -> out.put(name, Map.of(
                "failureCount", state.failureCount,
                "openUntil", state.openUntil == null ? "" : state.openUntil.toString())));
        return out;
    }

    private static class State {
        int failureCount;
        OffsetDateTime openUntil;
    }
}
