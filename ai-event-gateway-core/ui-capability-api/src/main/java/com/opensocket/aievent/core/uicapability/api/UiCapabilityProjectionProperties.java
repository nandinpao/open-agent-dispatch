package com.opensocket.aievent.core.uicapability.api;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ui-capability.projection")
public class UiCapabilityProjectionProperties {
    private int maximumCacheEntries = 10_000;
    private Duration maximumTtl = Duration.ofSeconds(60);
    public int getMaximumCacheEntries() { return maximumCacheEntries; }
    public void setMaximumCacheEntries(int value) { maximumCacheEntries = value; }
    public Duration getMaximumTtl() { return maximumTtl; }
    public void setMaximumTtl(Duration value) { maximumTtl = value; }
}
