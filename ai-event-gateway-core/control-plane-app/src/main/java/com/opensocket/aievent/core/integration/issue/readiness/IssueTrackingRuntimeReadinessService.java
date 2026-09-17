package com.opensocket.aievent.core.integration.issue.readiness;

import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.action.executor.AdapterActionExecutionProperties;
import com.opensocket.aievent.core.action.executor.AdapterExecutionAuthority;
import com.opensocket.aievent.core.action.executor.AdapterExecutionAuthorityPolicy;
import com.opensocket.aievent.core.integration.identity.*;
import com.opensocket.aievent.core.kernel.CoreVersion;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Server-side readiness authority for Source System Issue Tracking.
 *
 * <p>This reducer deliberately separates configuration, executable runtime, provider
 * authentication and live CREATE certification. A green "Active" mapping is never treated as
 * proof that the provider path is currently executable or runtime-certified.</p>
 */
@Service
public final class IssueTrackingRuntimeReadinessService {
    public static final String AUTHORITY = "V39_5_ISSUE_TRACKING_RUNTIME_READINESS_V1";

    private final IntegrationIdentityService identities;
    private final IntegrationCredentialMaterialVerifier credentialMaterials;
    private final AdapterExecutionAuthorityPolicy executionAuthority;
    private final AdapterActionExecutionProperties executionProperties;

    public IssueTrackingRuntimeReadinessService(
            IntegrationIdentityService identities,
            IntegrationCredentialMaterialVerifier credentialMaterials,
            AdapterExecutionAuthorityPolicy executionAuthority,
            AdapterActionExecutionProperties executionProperties) {
        this.identities = identities;
        this.credentialMaterials = credentialMaterials;
        this.executionAuthority = executionAuthority;
        this.executionProperties = executionProperties;
    }

    public IssueTrackingRuntimeReadiness evaluate(
            String tenantId,
            String sourceSystemId,
            String taskType,
            boolean liveProbe,
            String correlationId) {
        String source = required(sourceSystemId, "sourceSystemId");
        String task = blankToNull(taskType);
        List<String> blockers = new ArrayList<>();
        List<IssueTrackingReadinessCheck> checks = new ArrayList<>();

        List<IntegrationProjectMapping> sourceMappings = identities.mappings(tenantId, null, 1000).stream()
                .filter(mapping -> source.equals(mapping.sourceSystemId()))
                .filter(mapping -> mapping.taskType() == null || mapping.taskType().isBlank()
                        || (task != null && task.equalsIgnoreCase(mapping.taskType())))
                .sorted(Comparator
                        .comparing((IntegrationProjectMapping value) -> !ProjectMappingRuntimeReadiness.isReady(value))
                        .thenComparing((IntegrationProjectMapping value) -> value.taskType() != null && !value.taskType().isBlank())
                        .thenComparing(Comparator.comparingInt(IntegrationProjectMapping::resolutionPriority).reversed())
                        .thenComparing(IntegrationProjectMapping::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        IntegrationProjectMapping mapping = sourceMappings.isEmpty() ? null : sourceMappings.getFirst();

        boolean configured = mapping != null;
        List<String> mappingBlockers = ProjectMappingRuntimeReadiness.blockers(mapping);
        if (!configured) {
            blockers.add("ISSUE_PROJECT_MAPPING_NOT_CONFIGURED");
            checks.add(check("CONFIGURED", "Configured", "BLOCKED", "ISSUE_PROJECT_MAPPING_NOT_CONFIGURED",
                    "No Source-level Issue Tracking mapping is configured.", sourceRoute(source)));
        } else if (!mappingBlockers.isEmpty()) {
            blockers.addAll(mappingBlockers);
            checks.add(check("CONFIGURED", "Configured", "BLOCKED", "ISSUE_PROJECT_MAPPING_NOT_RUNTIME_READY",
                    "Issue Tracking is configured, but the active Project Mapping is not publish-ready: " + String.join(", ", mappingBlockers) + ".",
                    sourceRoute(source)));
        } else {
            checks.add(check("CONFIGURED", "Configured", "READY", "ISSUE_CONFIGURATION_READY",
                    "Source-level Project Mapping is ACTIVE + VALID with provider metadata evidence.", sourceRoute(source)));
        }

        boolean authorityReady = executionAuthority.authorityFor(AdapterType.ISSUE_TRACKING) == AdapterExecutionAuthority.CORE_GOVERNED;
        boolean autoExecuteReady = executionAuthority.shouldAutoExecuteInCore(AdapterType.ISSUE_TRACKING);
        boolean connectorRuntimeEnabled = executionProperties.getIssue().isConnectorRuntimeEnabled();
        if (!authorityReady) blockers.add(AdapterExecutionAuthorityPolicy.ISSUE_EXECUTOR_NOT_AVAILABLE);
        if (!autoExecuteReady) blockers.add("ISSUE_EXECUTOR_AUTO_EXECUTION_DISABLED");
        if (!connectorRuntimeEnabled) blockers.add("ISSUE_CONNECTOR_RUNTIME_DISABLED");

        ConnectorIntegrationExecutionContext context = null;
        IntegrationCredentialMaterialVerifier.CredentialMaterialReadiness material = null;
        String runtimeFailure = null;
        if (configured && mappingBlockers.isEmpty() && authorityReady && autoExecuteReady && connectorRuntimeEnabled) {
            try {
                context = identities.resolveConnectorExecutionContext(new MappingResolutionRequest(
                        tenantId, mapping.connectionId(), null, null, null, source, task));
                material = credentialMaterials.verify(context.credential());
                if (!material.materialResolvable()) {
                    runtimeFailure = firstNonBlank(material.errorCode(), "ISSUE_CREDENTIAL_SECRET_UNRESOLVABLE");
                    blockers.add(runtimeFailure);
                }
            } catch (RuntimeException ex) {
                runtimeFailure = reasonCode(ex, "ISSUE_CONNECTOR_RUNTIME_NOT_READY");
                blockers.add(runtimeFailure);
            }
        }
        boolean runtimeReady = configured && mappingBlockers.isEmpty() && authorityReady && autoExecuteReady
                && connectorRuntimeEnabled && context != null && material != null && material.materialResolvable();
        if (runtimeReady) {
            checks.add(check("RUNTIME_READY", "Runtime Ready", "READY", "ISSUE_RUNTIME_READY",
                    "Core-governed executor, connector context, technical Service Account and credential material are executable.",
                    "/settings/issue-tracking"));
        } else {
            String reason = firstNonBlank(runtimeFailure,
                    !authorityReady ? AdapterExecutionAuthorityPolicy.ISSUE_EXECUTOR_NOT_AVAILABLE : null,
                    !autoExecuteReady ? "ISSUE_EXECUTOR_AUTO_EXECUTION_DISABLED" : null,
                    !connectorRuntimeEnabled ? "ISSUE_CONNECTOR_RUNTIME_DISABLED" : null,
                    configured && !mappingBlockers.isEmpty() ? "ISSUE_PROJECT_MAPPING_NOT_RUNTIME_READY" : null,
                    "ISSUE_RUNTIME_WAITING_FOR_CONFIGURATION");
            checks.add(check("RUNTIME_READY", "Runtime Ready", configured ? "BLOCKED" : "WAITING", reason,
                    runtimeSummary(reason), runtimeRemediation(source, reason)));
        }

        boolean providerAuthenticated = false;
        boolean providerAuthKnown = false;
        String providerAuthReason = "ISSUE_PROVIDER_AUTHENTICATION_NOT_CHECKED";
        String providerAuthSummary = "Run a Redmine authentication check after runtime configuration is ready.";
        if (runtimeReady && context != null) {
            try {
                IntegrationPrincipal principal = context.principal();
                if (liveProbe) {
                    PermissionProbeResult probe = identities.probeConnectorExecutionContext(
                            tenantId, principal.principalId(), context.mapping().mappingId(), correlationId);
                    providerAuthenticated = probe.capabilityResults().getOrDefault(
                            IntegrationPermissionCapability.AUTHENTICATE, PermissionProbeResultStatus.DENIED)
                            == PermissionProbeResultStatus.GRANTED;
                    providerAuthKnown = true;
                    providerAuthReason = providerAuthenticated ? "ISSUE_PROVIDER_AUTHENTICATED" : "ISSUE_PROVIDER_AUTHENTICATION_FAILED";
                    providerAuthSummary = firstNonBlank(probe.providerResponseSummary(),
                            providerAuthenticated ? "Redmine authentication succeeded." : "Redmine authentication failed.");
                } else {
                    String persisted = principal.permissionSummary() == null ? null : principal.permissionSummary().get("AUTHENTICATE");
                    providerAuthKnown = principal.lastPermissionProbeAt() != null && persisted != null && !persisted.isBlank();
                    providerAuthenticated = providerAuthKnown && "GRANTED".equalsIgnoreCase(persisted);
                    providerAuthReason = providerAuthenticated ? "ISSUE_PROVIDER_AUTHENTICATED"
                            : providerAuthKnown ? "ISSUE_PROVIDER_AUTHENTICATION_FAILED" : "ISSUE_PROVIDER_AUTHENTICATION_NOT_CHECKED";
                    providerAuthSummary = providerAuthenticated
                            ? "Latest persisted Redmine authentication probe is GRANTED."
                            : providerAuthKnown ? "Latest persisted Redmine authentication probe is not GRANTED."
                            : "No persisted Redmine authentication proof is available. Review setup and run Test connection.";
                }
            } catch (RuntimeException ex) {
                providerAuthKnown = true;
                providerAuthReason = reasonCode(ex, "ISSUE_PROVIDER_AUTHENTICATION_FAILED");
                providerAuthSummary = firstNonBlank(ex.getMessage(), "Provider authentication probe failed.");
            }
        }
        if (!runtimeReady) {
            checks.add(check("PROVIDER_AUTHENTICATED", "Provider Authenticated", "WAITING",
                    "ISSUE_PROVIDER_AUTHENTICATION_WAITING_FOR_RUNTIME",
                    "Provider authentication is evaluated only after the governed runtime path is ready.", sourceRoute(source)));
        } else if (!providerAuthKnown) {
            checks.add(check("PROVIDER_AUTHENTICATED", "Provider Authenticated", "CHECK_REQUIRED", providerAuthReason,
                    providerAuthSummary, sourceRoute(source)));
        } else if (providerAuthenticated) {
            checks.add(check("PROVIDER_AUTHENTICATED", "Provider Authenticated", "READY", providerAuthReason,
                    providerAuthSummary, sourceRoute(source)));
        } else {
            blockers.add(providerAuthReason);
            checks.add(check("PROVIDER_AUTHENTICATED", "Provider Authenticated", "BLOCKED", providerAuthReason,
                    providerAuthSummary, sourceRoute(source)));
        }

        boolean liveCreateCertified = runtimeReady && providerAuthenticated
                && "CERTIFIED".equalsIgnoreCase(CoreVersion.RUNTIME_CERTIFICATION_STATUS);
        String certificationStatus = firstNonBlank(CoreVersion.RUNTIME_CERTIFICATION_STATUS, "NOT_CERTIFIED");
        checks.add(check("LIVE_CREATE_CERTIFIED", "Live Create Certified",
                liveCreateCertified ? "CERTIFIED" : "NOT_CERTIFIED",
                liveCreateCertified ? "ISSUE_LIVE_CREATE_CERTIFIED" : "ISSUE_LIVE_CREATE_NOT_CERTIFIED",
                liveCreateCertified
                        ? "This release carries live provider CREATE runtime certification evidence."
                        : "Configuration/readiness does not prove a real provider CREATE. Live CREATE remains not certified until the runtime certification gate passes.",
                "/operations/integration-sync"));

        String overallStatus;
        if (!configured || !runtimeReady || (providerAuthKnown && !providerAuthenticated)) overallStatus = "BLOCKED";
        else if (!providerAuthKnown) overallStatus = "CHECK_REQUIRED";
        else if (!liveCreateCertified) overallStatus = "READY_NOT_CERTIFIED";
        else overallStatus = "CERTIFIED";

        return new IssueTrackingRuntimeReadiness(
                source, task, overallStatus, configured, runtimeReady, providerAuthenticated, liveCreateCertified,
                certificationStatus, executionAuthority.authorityFor(AdapterType.ISSUE_TRACKING).name(), autoExecuteReady,
                connectorRuntimeEnabled,
                mapping == null ? null : mapping.connectionId(), mapping == null ? null : mapping.mappingId(),
                mapping == null ? null : mapping.externalProjectId(), mapping == null ? null : mapping.externalProjectKey(),
                mapping == null ? null : mapping.externalTrackerId(), context == null ? null : context.principal().principalId(),
                context == null ? null : context.credential().credentialId(), distinct(blockers), checks,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static IssueTrackingReadinessCheck check(String code, String label, String status, String reason,
                                                       String summary, String remediationRoute) {
        return new IssueTrackingReadinessCheck(code, label, status, reason, summary, remediationRoute);
    }

    private static String runtimeSummary(String reason) {
        return switch (reason == null ? "" : reason) {
            case AdapterExecutionAuthorityPolicy.ISSUE_EXECUTOR_NOT_AVAILABLE -> "The canonical Core-governed Issue executor is not available.";
            case "ISSUE_EXECUTOR_AUTO_EXECUTION_DISABLED" -> "Issue actions cannot advance automatically because Core auto-execution is disabled.";
            case "ISSUE_CONNECTOR_RUNTIME_DISABLED" -> "The canonical Issue Connector Runtime is disabled.";
            case "ISSUE_PROJECT_MAPPING_NOT_RUNTIME_READY" -> "Project Mapping must be ACTIVE + VALID with provider metadata evidence.";
            default -> "The Source-level Issue connector context is not currently executable: " + firstNonBlank(reason, "unknown blocker") + ".";
        };
    }

    private static String runtimeRemediation(String source, String reason) {
        if (AdapterExecutionAuthorityPolicy.ISSUE_EXECUTOR_NOT_AVAILABLE.equals(reason)
                || "ISSUE_EXECUTOR_AUTO_EXECUTION_DISABLED".equals(reason)
                || "ISSUE_CONNECTOR_RUNTIME_DISABLED".equals(reason)) return "/settings/issue-tracking";
        return sourceRoute(source);
    }

    private static String sourceRoute(String source) {
        return "/source-systems";
    }

    private static String reasonCode(RuntimeException ex, String fallback) {
        String value = ex == null ? null : ex.getMessage();
        if (value == null || value.isBlank()) return fallback;
        int marker = value.indexOf(':');
        String code = marker > 0 ? value.substring(0, marker) : value;
        code = code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
        return code.isBlank() ? fallback : code;
    }

    private static List<String> distinct(List<String> values) {
        return values.stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).distinct().toList();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required.");
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }
}
