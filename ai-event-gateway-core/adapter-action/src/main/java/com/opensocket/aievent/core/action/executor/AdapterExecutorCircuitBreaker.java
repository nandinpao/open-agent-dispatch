package com.opensocket.aievent.core.action.executor;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdapterExecutorCircuitBreaker {
    private final AdapterExecutorRuntimeConfigurationView runtimeConfiguration;
    private final Clock clock;
    private final Map<String, CircuitState> states = new ConcurrentHashMap<>();

    @Autowired
    public AdapterExecutorCircuitBreaker(AdapterActionExecutionProperties properties, AdapterExecutorRuntimeConfigurationView runtimeConfiguration) {
        this(properties, runtimeConfiguration, Clock.systemUTC());
    }

    /** Compatibility constructor for focused unit tests; production injection uses the typed runtime view. */
    public AdapterExecutorCircuitBreaker(AdapterActionExecutionProperties properties) {
        this(properties, new AdapterExecutorRuntimeConfigurationView(properties, null), Clock.systemUTC());
    }

    /** Package-private deterministic-time constructor for concurrency/expiry verification. */
    AdapterExecutorCircuitBreaker(AdapterActionExecutionProperties properties,
            AdapterExecutorRuntimeConfigurationView runtimeConfiguration, Clock clock) {
        this.runtimeConfiguration = runtimeConfiguration;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public boolean isOpen(String executorName) {
        if (!runtimeConfiguration.circuitBreakerEnabled() || executorName == null || executorName.isBlank()) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        CircuitState state = states.computeIfPresent(executorName, (key, current) ->
                current.isOpenAt(now) ? current : current.openUntil() == null ? current : null);
        return state != null && state.isOpenAt(now);
    }

    public OffsetDateTime openUntil(String executorName) {
        if (executorName == null) return null;
        OffsetDateTime now = OffsetDateTime.now(clock);
        CircuitState state = states.computeIfPresent(executorName, (key, current) ->
                current.isOpenAt(now) ? current : current.openUntil() == null ? current : null);
        return state == null ? null : state.openUntil();
    }

    public void recordSuccess(String executorName) {
        if (executorName != null) states.remove(executorName);
    }

    public void recordFailure(String executorName) {
        if (!runtimeConfiguration.circuitBreakerEnabled() || executorName == null || executorName.isBlank()) return;
        int threshold = runtimeConfiguration.circuitBreakerFailureThreshold();
        var openDuration = runtimeConfiguration.circuitBreakerOpenDuration();
        OffsetDateTime now = OffsetDateTime.now(clock);
        states.compute(executorName, (key, current) -> {
            CircuitState effective = current;
            if (effective != null && effective.openUntil() != null) {
                if (effective.isOpenAt(now)) return effective;
                effective = null;
            }
            int nextFailureCount = effective == null ? 1 : effective.failureCount() + 1;
            if (nextFailureCount >= threshold) {
                return new CircuitState(nextFailureCount, now.plus(openDuration));
            }
            return new CircuitState(nextFailureCount, null);
        });
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        states.forEach((name, state) -> out.put(name, Map.of(
                "failureCount", state.failureCount(),
                "openUntil", state.openUntil() == null ? "" : state.openUntil().toString())));
        return out;
    }

    private record CircuitState(int failureCount, OffsetDateTime openUntil) {
        boolean isOpenAt(OffsetDateTime now) {
            return openUntil != null && openUntil.isAfter(now);
        }
    }
}
