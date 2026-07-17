package com.opensocket.aievent.core.summary;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "incident.summary")
public class IncidentSummaryProperties {
    private String store = "MEMORY";
    private Duration window = Duration.ofMinutes(5);

    public String getStore() { return store; }
    public void setStore(String store) { this.store = store; }
    public Duration getWindow() { return window; }
    public void setWindow(Duration window) { this.window = window; }
}
