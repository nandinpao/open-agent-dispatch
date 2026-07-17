package com.opensocket.aievent.core.observability;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("aiEventGatewayCore")
public class CoreOperationalHealthIndicator implements HealthIndicator {
    private final OperationalSummaryService summaryService;
    private final ObservabilityProperties properties;

    public CoreOperationalHealthIndicator(OperationalSummaryService summaryService,
                                          ObservabilityProperties properties) {
        this.summaryService = summaryService;
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!properties.isEnabled() || !properties.isHealthIndicatorEnabled()) {
            return Health.unknown().withDetail("reason", "core observability health indicator disabled").build();
        }
        return Health.up().withDetails(summaryService.stores()).build();
    }
}
