package com.opensocket.aievent.core.integration.identity;

/**
 * Runtime context for the thin Issue connector path.
 *
 * <p>This context intentionally carries no {@link IntegrationOperation},
 * operation-scoped principal policy or Permission Probe result. It resolves a
 * server-side Project Mapping, the Connection execution identity and one valid
 * credential reference only. External Issue authorization remains owned by the
 * provider.</p>
 */
public record ConnectorIntegrationExecutionContext(
        IntegrationConnection connection,
        IntegrationPrincipal principal,
        IntegrationCredentialMetadata credential,
        IntegrationProjectMapping mapping) {
}
