package com.opensocket.aievent.core.iam.api.response;
public record FederationProviderPublicResponse(String tenantId,String providerId,String providerCode,String providerType,String displayName,boolean defaultProvider,boolean upstreamMfaRequired) {}
