package com.opensocket.aievent.core.iam.api.response;
import java.time.Instant;
public record ExternalIdentityLinkResponse(String credentialLinkId,String tenantId,String providerId,String providerCode,String providerType,String issuerUri,String externalSubject,String upstreamUsername,String upstreamEmail,Boolean upstreamEmailVerified,String userId,String canonicalUsername,String status,Instant lastAuthenticatedAt,long version) {}
