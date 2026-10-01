package com.opensocket.aievent.core.kernel.configuration;

/** Runtime mutability is granted by verified consumer capability, not by storage location. */
public enum ConfigurationMutability {
    HOT_IMMEDIATE,
    HOT_NEXT_CYCLE,
    RESTART_REQUIRED,
    IMMUTABLE
}
