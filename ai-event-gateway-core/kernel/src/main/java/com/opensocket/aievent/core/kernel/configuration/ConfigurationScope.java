package com.opensocket.aievent.core.kernel.configuration;

/** V40 generic runtime-configuration scope boundary. */
public enum ConfigurationScope {
    SYSTEM(true),
    COMPONENT(true),
    NODE_ROLE(true),
    TENANT(false),
    DEPARTMENT(false),
    SOURCE_SYSTEM(false),
    PROVIDER(false),
    FLOW(false),
    AGENT(false),
    USER(false);

    private final boolean genericRuntimeConfigSupported;

    ConfigurationScope(boolean genericRuntimeConfigSupported) {
        this.genericRuntimeConfigSupported = genericRuntimeConfigSupported;
    }

    public boolean genericRuntimeConfigSupported() {
        return genericRuntimeConfigSupported;
    }
}
