package com.opensocket.aievent.core.kernel.configuration;

/** How a runtime consumer obtains a configuration value. */
public enum ConfigurationConsumerContract {
    RUNTIME_SNAPSHOT,
    DYNAMIC_SCHEDULER,
    STARTUP_BINDING,
    SPRING_CONDITION,
    DEPLOYMENT_ONLY
}
