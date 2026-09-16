package com.opensocket.aievent.core.resourceaccess.enforcement;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.ResourceDecisionEvidenceRepository;
import com.opensocket.aievent.core.resourceaccess.core.ResourceFieldPolicySelector;
import com.opensocket.aievent.core.resourceaccess.core.ResourcePolicyScale;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Fixed-policy export authorization. Business modules produce rows only after receiving this evidence. */
@Component
@ConditionalOnProperty(prefix = "resource-access", name = "export-enabled", havingValue = "true")
public class DefaultResourceExportAuthorizationService implements ResourceExportAuthorizationPort {
    private final ResourceAccessEnforcementPort enforcement;
    private final ResourceEnforcementContextPort contexts;
    private final RuntimeAuthorizationLeasePort leases;
    private final ResourceDecisionEvidenceRepository evidence;
    private final ResourceExportAuthorizationRepositoryPort repository;
    private final long maximumRows;
    private final Clock clock;

    public DefaultResourceExportAuthorizationService(
            ResourceAccessEnforcementPort enforcement,
            ResourceEnforcementContextPort contexts,
            RuntimeAuthorizationLeasePort leases,
            ResourceDecisionEvidenceRepository evidence,
            ResourceExportAuthorizationRepositoryPort repository,
            @Value("${resource-access.export.maximum-rows:100000}") long maximumRows,
            Clock resourceAccessClock) {
        this.enforcement = Objects.requireNonNull(enforcement);
        this.contexts = Objects.requireNonNull(contexts);
        this.leases = Objects.requireNonNull(leases);
        this.evidence = Objects.requireNonNull(evidence);
        this.repository = Objects.requireNonNull(repository);
        this.maximumRows = Math.max(1, maximumRows);
        this.clock = Objects.requireNonNull(resourceAccessClock);
    }

    @Override
    public ResourceExportAuthorization authorize(ResourceExportCommand command) {
        ResourceEnforcementContext context = contexts.current();
        validateTrustedContext(command.resourceRef(), command.correlationId(), context);
        if (command.estimatedRowCount() > maximumRows) throw new IllegalStateException("EXPORT_ROW_LIMIT_EXCEEDED");

        PermissionPair pair = permissions(command.resourceRef().resourceType());
        AuthorizationDecision read = enforcement.authorize(new ResourceEnforcementCommand(
                new ResourceAction(pair.read(), ResourceAction.ActionKind.READ, false),
                command.resourceRef(),
                VisibilityLevel.STANDARD,
                RequestChannel.EXPORT,
                command.purpose(),
                OperationPhase.START,
                SecurityEpoch.ZERO,
                command.trustedFlowContext()));
        AuthorizationDecision export = enforcement.authorize(new ResourceEnforcementCommand(
                new ResourceAction(pair.export(), ResourceAction.ActionKind.EXPORT, true),
                command.resourceRef(),
                VisibilityLevel.STANDARD,
                RequestChannel.EXPORT,
                command.purpose(),
                OperationPhase.BEFORE_SIDE_EFFECT,
                read.securityEpoch(),
                command.trustedFlowContext()));
        executable(read, "EXPORT_READ_NOT_EXECUTABLE");
        executable(export, "EXPORT_PERMISSION_NOT_EXECUTABLE");

        List<String> allowed = authorizedFields(command, export);
        String fieldHash = hash(String.join("\n", allowed));
        AuthorizationRequest leaseRequest = new AuthorizationRequest(
                context.authentication().principal(),
                context.authentication(),
                context.authentication().activeTenant(),
                new ResourceAction(pair.export(), ResourceAction.ActionKind.EXPORT, true),
                command.resourceRef(),
                VisibilityLevel.STANDARD,
                RequestChannel.EXPORT,
                command.purpose(),
                OperationPhase.START,
                "",
                context.correlationId(),
                export.securityEpoch(),
                command.trustedFlowContext());
        RuntimeAuthorizationLease lease = leases.issue(leaseRequest, export);
        ResourceExportAuthorization authorization = new ResourceExportAuthorization(
                "export-auth-" + UUID.randomUUID(),
                command.resourceRef(),
                context.authentication().principal().principalId(),
                command.format(),
                allowed,
                fieldHash,
                Math.min(maximumRows, command.estimatedRowCount() == 0 ? maximumRows : command.estimatedRowCount()),
                read.decisionId(),
                export.decisionId(),
                export.policyVersion(),
                export.securityEpoch(),
                export.descriptorHash(),
                lease.leaseId(),
                lease.fencingVersion(),
                clock.instant(),
                lease.expiresAt(),
                true);
        try {
            ResourceExportAuthorization saved = repository.save(
                    authorization,
                    context.authentication().principal().principalId(),
                    command.purpose(),
                    command.idempotencyKey(),
                    command.correlationId());
            if (!saved.runtimeLeaseId().equals(lease.leaseId())) {
                safeRevoke(
                        lease,
                        "EXPORT_IDEMPOTENT_REPLAY_UNUSED_LEASE",
                        command.correlationId());
            }
            return saved;
        } catch (RuntimeException exception) {
            safeRevoke(lease, "EXPORT_AUTHORIZATION_PERSISTENCE_ABORTED", command.correlationId());
            throw exception;
        }
    }

    @Override
    @Transactional
    public ExportArtifactCommitResult authorizeCommit(ExportArtifactCommitCommand command) {
        ResourceEnforcementContext context = contexts.current();
        if (!command.tenantId().equals(context.authentication().activeTenant().tenantId())) {
            throw new IllegalArgumentException("EXPORT_TENANT_CONTEXT_MISMATCH");
        }
        if (!command.correlationId().equals(context.correlationId())) {
            throw new IllegalArgumentException("EXPORT_CORRELATION_CONTEXT_MISMATCH");
        }
        ResourceExportAuthorization authorization = repository.find(
                        command.tenantId(), command.exportAuthorizationId())
                .orElseThrow(() -> new IllegalArgumentException("EXPORT_AUTHORIZATION_NOT_FOUND"));
        if (!authorization.executable()) return reject(authorization, command, "EXPORT_AUTHORIZATION_NOT_EXECUTABLE");
        if (!authorization.principalId().equals(context.authentication().principal().principalId())) {
            return reject(authorization, command, "EXPORT_PRINCIPAL_MISMATCH");
        }
        if (!authorization.runtimeLeaseId().equals(command.runtimeLeaseId())) {
            return reject(authorization, command, "EXPORT_RUNTIME_LEASE_MISMATCH");
        }
        if (authorization.fencingVersion() != command.presentedFencingVersion()) {
            return reject(authorization, command, "EXPORT_FENCING_VERSION_MISMATCH");
        }
        if (!authorization.fieldSetHash().equals(command.fieldSetHash())) {
            return reject(authorization, command, "EXPORT_FIELD_SET_DRIFT");
        }
        if (command.rowCount() > authorization.maximumRows()) {
            return reject(authorization, command, "EXPORT_ROW_COUNT_EXCEEDED");
        }

        RuntimeAuthorizationLease checked = leases.check(new RuntimeAuthorizationCheckpoint(
                command.tenantId(),
                command.runtimeLeaseId(),
                OperationPhase.BEFORE_RESULT_COMMIT,
                command.presentedFencingVersion(),
                command.correlationId()));
        if (checked.status() != RuntimeLeaseStatus.ACTIVE) {
            return reject(authorization, command, "EXPORT_RUNTIME_AUTHORIZATION_" + checked.status());
        }
        leases.complete(command.tenantId(), command.runtimeLeaseId(), command.correlationId());
        ExportArtifactCommitResult result = new ExportArtifactCommitResult(
                authorization.exportAuthorizationId(),
                authorization.runtimeLeaseId(),
                command.rowCount(),
                command.fieldSetHash(),
                command.artifactSha256(),
                command.artifactSizeBytes(),
                clock.instant(),
                true,
                "EXPORT_ARTIFACT_COMMIT_ALLOWED");
        repository.appendCommit(
                result,
                command.tenantId(),
                context.authentication().principal().principalId(),
                command.correlationId());
        return result;
    }

    private ExportArtifactCommitResult reject(
            ResourceExportAuthorization authorization,
            ExportArtifactCommitCommand command,
            String reason) {
        ExportArtifactCommitResult result = new ExportArtifactCommitResult(
                authorization.exportAuthorizationId(),
                authorization.runtimeLeaseId(),
                command.rowCount(),
                command.fieldSetHash(),
                command.artifactSha256(),
                command.artifactSizeBytes(),
                clock.instant(),
                false,
                reason);
        String principal = contexts.current().authentication().principal().principalId();
        repository.appendCommit(result, command.tenantId(), principal, command.correlationId());
        throw new IllegalStateException(reason);
    }

    private List<String> authorizedFields(ResourceExportCommand command, AuthorizationDecision decision) {
        VisibilityPolicyRecord policy = evidence.findActiveVisibilityPolicy(
                        command.resourceRef().tenantId(), command.resourceRef().resourceType())
                .orElseThrow(() -> new IllegalStateException("EXPORT_FIELD_POLICY_REQUIRED"));
        List<String> fields = new ArrayList<>();
        for (String field : command.requestedFields()) {
            VisibilityFieldRule rule = ResourceFieldPolicySelector.select(policy, field)
                    .orElseThrow(() -> new IllegalStateException("EXPORT_FIELD_NOT_CLASSIFIED:" + field));
            if (!rule.exportAllowed()) throw new IllegalStateException("EXPORT_FIELD_POLICY_DENIED:" + field);
            if (!ResourcePolicyScale.visibilityCovers(decision.grantedVisibility(), rule.minimumVisibilityLevel())) {
                throw new IllegalStateException("EXPORT_FIELD_VISIBILITY_INSUFFICIENT:" + field);
            }
            fields.add(field);
        }
        if (fields.isEmpty()) throw new IllegalArgumentException("At least one export field is required");
        return List.copyOf(fields);
    }

    private void safeRevoke(RuntimeAuthorizationLease lease, String reason, String correlationId) {
        try {
            leases.revoke(lease.resourceRef().tenantId(), lease.leaseId(), reason, correlationId);
        } catch (RuntimeException ignored) {
            // The original failure remains authoritative; lease reconciliation will surface any terminal race.
        }
    }

    private static void executable(AuthorizationDecision decision, String reason) {
        if (decision.mode() != AuthorizationDecisionMode.FORMAL
                || decision.effect() != DecisionEffect.ALLOW
                || decision.shadowOnly()) {
            throw new IllegalStateException(reason);
        }
    }

    private static PermissionPair permissions(ResourceType type) {
        return switch (type) {
            case TASK, TASK_CHAIN, TASK_RESULT, TASK_CONTEXT_SNAPSHOT ->
                    new PermissionPair("task.read", "task.export");
            case TASK_ISSUE_LINK, ISSUE_CONTEXT_SNAPSHOT, ISSUE_CONFLICT, ISSUE_DEAD_LETTER, ISSUE_TOPOLOGY,
                    ISSUE_CONNECTION, ISSUE_PRINCIPAL, ISSUE_CREDENTIAL_METADATA, ISSUE_PROJECT_MAPPING ->
                    new PermissionPair("integration.issue.read", "integration.issue.export");
            default -> throw new IllegalArgumentException("Export is not supported for resource type " + type);
        };
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void validateTrustedContext(
            ResourceRef ref, String correlation, ResourceEnforcementContext context) {
        if (!ref.tenantId().equals(context.authentication().activeTenant().tenantId())) {
            throw new IllegalArgumentException("EXPORT_TENANT_CONTEXT_MISMATCH");
        }
        if (!correlation.equals(context.correlationId())) {
            throw new IllegalArgumentException("EXPORT_CORRELATION_CONTEXT_MISMATCH");
        }
    }

    private record PermissionPair(String read, String export) {}
}
