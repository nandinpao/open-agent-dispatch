package com.opensocket.aievent.core.processing;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "core.decision")
public class EventProcessingProperties {
    private Duration dedupWindow = Duration.ofMinutes(5);
    private Duration dedupTtl = Duration.ofHours(2);

    public Duration getDedupWindow() {
        return dedupWindow;
    }

    public void setDedupWindow(Duration dedupWindow) {
        this.dedupWindow = dedupWindow == null ? Duration.ofMinutes(5) : dedupWindow;
    }

    public Duration getDedupTtl() {
        return dedupTtl;
    }

    public void setDedupTtl(Duration dedupTtl) {
        this.dedupTtl = dedupTtl == null ? Duration.ofHours(2) : dedupTtl;
    }
}
