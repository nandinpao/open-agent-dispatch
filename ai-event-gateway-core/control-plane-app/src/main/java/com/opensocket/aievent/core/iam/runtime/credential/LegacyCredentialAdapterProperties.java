package com.opensocket.aievent.core.iam.runtime.credential;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Narrow compatibility configuration for verifying an already linked legacy password.
 * This adapter can never create a browser session, principal, role, permission or account bridge.
 */
@ConfigurationProperties(prefix = "aeg.iam.credentials")
public class LegacyCredentialAdapterProperties {
    private boolean legacyPasswordAdapterEnabled;

    public boolean isLegacyPasswordAdapterEnabled() {
        return legacyPasswordAdapterEnabled;
    }

    public void setLegacyPasswordAdapterEnabled(boolean legacyPasswordAdapterEnabled) {
        this.legacyPasswordAdapterEnabled = legacyPasswordAdapterEnabled;
    }
}
