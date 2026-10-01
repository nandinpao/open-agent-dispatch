package com.opensocket.aievent.core.observability;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("aiEventGatewayCore")
public class CoreOperationalHealthIndicator implements HealthIndicator {
    private final OperationalSummaryService summaryService;
    private final CoreObservabilityRuntimeConfigurationView runtimeConfiguration;

    public CoreOperationalHealthIndicator(OperationalSummaryService summaryService,
                                          CoreObservabilityRuntimeConfigurationView runtimeConfiguration) {
        this.summaryService = summaryService;
        this.runtimeConfiguration = runtimeConfiguration;
    }

    @Override
    public Health health() {
        if (!runtimeConfiguration.enabled() || !runtimeConfiguration.healthIndicatorEnabled()) {
            return Health.unknown().withDetail("reason", "core observability health indicator disabled").build();
        }
        return Health.up().withDetails(summaryService.stores()).build();
    }
}
