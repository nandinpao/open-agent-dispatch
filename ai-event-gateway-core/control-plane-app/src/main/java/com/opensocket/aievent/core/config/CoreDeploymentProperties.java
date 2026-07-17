package com.opensocket.aievent.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "core.deployment")
public class CoreDeploymentProperties {
    public enum Mode {
        MODULAR_MONOLITH,
        HYBRID_ADAPTER_WORKER,
        EXTERNALIZED_CONTROL_PLANE
    }

    private Mode mode = Mode.MODULAR_MONOLITH;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.MODULAR_MONOLITH : mode;
    }
}
