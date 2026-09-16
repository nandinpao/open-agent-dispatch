package com.opensocket.aievent.core.iam.token.application.result;

/** Result from the non-RLS PAT directory. No authorization authority is carried here. */
public record ResolvedPersonalAccessTokenTenantResult(String tenantId, String tokenPrefix) {}
