package com.opensocket.aievent.core.iam.token.application.command;

/** Resolves only the non-secret public prefix of a PAT to its authoritative Tenant. */
public record ResolvePersonalAccessTokenTenantCommand(String presentedToken) {}
