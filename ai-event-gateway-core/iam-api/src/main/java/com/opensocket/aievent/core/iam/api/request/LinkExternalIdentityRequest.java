package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record LinkExternalIdentityRequest(@NotBlank @Size(max=128) String providerId,@NotBlank @Size(max=320) String externalSubject,@Size(max=320) String upstreamUsername,@Size(max=320) String upstreamEmail,boolean upstreamEmailVerified) {}
