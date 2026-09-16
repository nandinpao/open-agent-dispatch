package com.opensocket.aievent.core.iam.api.response;
import java.util.List;
public record AuthenticationProviderResponse(String tenantId,String providerId,String providerCode,String providerType,String displayName,String status,String issuerUri,String clientId,String clientSecretRef,List<String> scopes,String subjectClaim,String usernameClaim,String emailClaim,String displayNameClaim,String amrClaim,String acrClaim,String upstreamMfaMode,List<String> trustedAmrValues,List<String> trustedAcrValues,String linkMode,String jitMode,boolean authorizationEnabled,long version) {}
