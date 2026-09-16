package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpsertAuthenticationProviderRequest(
        @NotBlank @Size(max=128) String providerId,
        @NotBlank @Size(max=80) String providerCode,
        @NotBlank @Size(max=24) String providerType,
        @NotBlank @Size(max=160) String displayName,
        @NotBlank @Size(max=1024) String issuerUri,
        @NotBlank @Size(max=320) String clientId,
        @NotBlank @Size(max=320) String clientSecretRef,
        @NotEmpty List<@Size(max=128) String> scopes,
        @Size(max=96) String subjectClaim,
        @Size(max=96) String usernameClaim,
        @Size(max=96) String emailClaim,
        @Size(max=96) String displayNameClaim,
        @Size(max=96) String amrClaim,
        @Size(max=96) String acrClaim,
        @NotBlank @Size(max=40) String upstreamMfaMode,
        List<@Size(max=96) String> trustedAmrValues,
        List<@Size(max=256) String> trustedAcrValues,
        @NotBlank @Size(max=48) String linkMode,
        @NotBlank @Size(max=48) String jitMode,
        boolean authorizationEnabled) {}
