package com.opensocket.aievent.core.incident;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Incident-owned subset of core.lifecycle.incident configuration. */
@ConfigurationProperties(prefix = "core.lifecycle.incident")
public class IncidentModuleProperties {
    private ReopenPolicy reopenPolicy = ReopenPolicy.REOPEN_RECENT;
    private Duration reopenWindow = Duration.ofHours(24);

    public enum ReopenPolicy {
        CREATE_NEW,
        REOPEN_RECENT
    }

    public ReopenPolicy getReopenPolicy() {
        return reopenPolicy;
    }

    public void setReopenPolicy(ReopenPolicy reopenPolicy) {
        this.reopenPolicy = reopenPolicy == null ? ReopenPolicy.REOPEN_RECENT : reopenPolicy;
    }

    public Duration getReopenWindow() {
        return reopenWindow;
    }

    public void setReopenWindow(Duration reopenWindow) {
        this.reopenWindow = reopenWindow == null ? Duration.ofHours(24) : reopenWindow;
    }
}
