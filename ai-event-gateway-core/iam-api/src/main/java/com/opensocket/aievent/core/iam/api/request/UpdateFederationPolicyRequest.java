package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.Size;
public record UpdateFederationPolicyRequest(boolean localLoginEnabled, boolean oidcLoginEnabled, boolean samlLoginEnabled, boolean providerDiscoveryEnabled, @Size(max=128) String defaultProviderId) {}
