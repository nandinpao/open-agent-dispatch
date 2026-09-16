package com.opensocket.aievent.core.iam.api.response;
import java.time.Instant;
public record OidcStartResponse(String authorizationUrl,String providerId,String providerName,Instant expiresAt) {}
