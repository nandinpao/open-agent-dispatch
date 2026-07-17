package com.opensocket.aievent.gateway.netty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for Admin audit event persistence boundaries. */
@ConfigurationProperties(prefix = "audit")
public class AuditLogProperties {

    /** Enables audit event capture. In-memory Admin events are always captured; this gates persistence. */
    private boolean persistenceEnabled = false;

    /** Declares the configured audit sink. P8.13 ships with NOOP only; JDBC/FILE can be added later. */
    private String sink = "NOOP";

    /** Optional directory reserved for a future FILE sink. */
    private String spoolDirectory = "./data/audit-events";

    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    public void setPersistenceEnabled(boolean persistenceEnabled) {
        this.persistenceEnabled = persistenceEnabled;
    }

    public String getSink() {
        return sink;
    }

    public void setSink(String sink) {
        this.sink = sink == null || sink.isBlank() ? "NOOP" : sink.trim().toUpperCase();
    }

    public String getSpoolDirectory() {
        return spoolDirectory;
    }

    public void setSpoolDirectory(String spoolDirectory) {
        this.spoolDirectory = spoolDirectory == null || spoolDirectory.isBlank() ? "./data/audit-events" : spoolDirectory.trim();
    }

    public boolean persistenceEnabled() {
        return persistenceEnabled;
    }

    public String sink() {
        return sink;
    }

    public String spoolDirectory() {
        return spoolDirectory;
    }
}
