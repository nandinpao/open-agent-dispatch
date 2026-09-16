package com.opensocket.aievent.core.iam.api.response;
public record FederationPolicyResponse(String tenantId,boolean localLoginEnabled,boolean oidcLoginEnabled,boolean samlLoginEnabled,boolean providerDiscoveryEnabled,String defaultProviderId,long version) {}
