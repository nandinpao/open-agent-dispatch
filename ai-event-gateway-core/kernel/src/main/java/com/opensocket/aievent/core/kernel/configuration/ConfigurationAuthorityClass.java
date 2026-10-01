package com.opensocket.aievent.core.kernel.configuration;

/** Single-authority classification for governed OpenDispatch configuration. */
public enum ConfigurationAuthorityClass {
    BOOTSTRAP_INFRA(false),
    SECURITY_INVARIANT(false),
    RUNTIME_TUNABLE(true),
    DOMAIN_CONFIG(false),
    SECRET_MATERIAL(false),
    TEST_RELEASE_CONFIG(false);

    private final boolean genericRuntimeConfigEligible;

    ConfigurationAuthorityClass(boolean genericRuntimeConfigEligible) {
        this.genericRuntimeConfigEligible = genericRuntimeConfigEligible;
    }

    public boolean genericRuntimeConfigEligible() {
        return genericRuntimeConfigEligible;
    }
}
