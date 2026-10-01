package com.opensocket.aievent.worker.configuration;

import java.util.Arrays;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** PRD may not silently disable the remote Runtime Configuration authority plane. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class WorkerRuntimeConfigurationProductionGuard implements ApplicationRunner {
    private final WorkerRuntimeConfigurationProperties properties;
    private final String environment;
    private final Environment springEnvironment;

    @Autowired
    public WorkerRuntimeConfigurationProductionGuard(
            WorkerRuntimeConfigurationProperties properties,
            Environment springEnvironment,
            @Value("${opendispatch.environment:LOCAL}") String environment) {
        this.properties = properties;
        this.springEnvironment = springEnvironment;
        this.environment = canonical(environment);
    }

    /** Focused-test constructor. */
    WorkerRuntimeConfigurationProductionGuard(
            WorkerRuntimeConfigurationProperties properties,
            String environment) {
        this.properties = properties;
        this.springEnvironment = null;
        this.environment = canonical(environment);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!production()) return;
        if (!properties.enabled()) {
            throw new IllegalStateException("PRD requires adapter-worker.runtime-configuration.enabled=true");
        }
        if (!properties.failClosedOnColdStart()) {
            throw new IllegalStateException("PRD requires adapter-worker.runtime-configuration.fail-closed-on-cold-start=true");
        }
        if (!properties.lkgEnabled()) {
            throw new IllegalStateException("PRD requires adapter-worker.runtime-configuration.lkg-enabled=true");
        }
        if (properties.maxStaleMs() <= 0) {
            throw new IllegalStateException("PRD requires adapter-worker.runtime-configuration.max-stale-ms>0");
        }
        properties.requireSecureKey();
    }

    private boolean production() {
        if ("PRD".equals(environment)) return true;
        return springEnvironment != null && Arrays.stream(springEnvironment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile));
    }

    private static String canonical(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
