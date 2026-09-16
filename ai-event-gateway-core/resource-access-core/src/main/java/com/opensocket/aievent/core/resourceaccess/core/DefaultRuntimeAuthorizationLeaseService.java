package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Expiring, epoch-bound and fenceable authorization for streaming, export, attachment and background execution. */
public final class DefaultRuntimeAuthorizationLeaseService implements RuntimeAuthorizationLeasePort {
    private final RuntimeAuthorizationLeaseRepository repository;
    private final ResourceDecisionEvidenceRepository evidence;
    private final Clock clock;
    private final Duration readTtl;
    private final Duration writeTtl;
    private final Duration readRecheck;
    private final Duration restrictedRecheck;

    public DefaultRuntimeAuthorizationLeaseService(
            RuntimeAuthorizationLeaseRepository repository,
            ResourceDecisionEvidenceRepository evidence,
            Clock clock,
            Duration readTtl,
            Duration writeTtl,
            Duration readRecheck,
            Duration restrictedRecheck) {
        this.repository = Objects.requireNonNull(repository);
        this.evidence = Objects.requireNonNull(evidence);
        this.clock = Objects.requireNonNull(clock);
        this.readTtl = positive(readTtl, Duration.ofMinutes(15));
        this.writeTtl = positive(writeTtl, Duration.ofMinutes(5));
        this.readRecheck = positive(readRecheck, Duration.ofSeconds(30));
        this.restrictedRecheck = positive(restrictedRecheck, Duration.ofSeconds(10));
    }

    @Override
    public RuntimeAuthorizationLease issue(AuthorizationRequest request, AuthorizationDecision decision) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(decision, "decision");
        if (decision.mode() != AuthorizationDecisionMode.FORMAL
                || decision.effect() != DecisionEffect.ALLOW
                || decision.shadowOnly()) {
            throw new IllegalStateException("RUNTIME_LEASE_REQUIRES_EXECUTABLE_FORMAL_ALLOW");
        }
        if (!decision.resourceRef().equals(request.resourceRef())
                || !decision.permissionCode().equals(request.action().permissionCode())) {
            throw new IllegalArgumentException("Decision and request do not match");
        }
        if (decision.descriptorHash().isBlank()) {
            throw new IllegalArgumentException("RUNTIME_LEASE_REQUIRES_DESCRIPTOR_HASH");
        }

        Instant now = clock.instant();
        Duration ttl = request.action().sideEffecting() ? writeTtl : readTtl;
        Duration hardStaleWindow = riskRecheck(request, decision);
        Duration softRecheckWindow = half(hardStaleWindow);
        Instant expires = now.plus(ttl);
        Instant maximumStaleUntil = minimum(now.plus(hardStaleWindow), expires);
        Instant recheckAt = minimum(now.plus(softRecheckWindow), maximumStaleUntil);
        String assignment = request.trustedFlowContext().getOrDefault("assignmentId", "");
        Integer attempt = parseAttempt(request.trustedFlowContext().get("attemptNo"));

        RuntimeAuthorizationLease draft = new RuntimeAuthorizationLease(
                "ral-" + UUID.randomUUID(),
                decision.decisionId(),
                decision.descriptorHash(),
                request.resourceRef(),
                request.principal(),
                request.action().permissionCode(),
                assignment,
                attempt,
                decision.policyVersion(),
                decision.securityEpoch(),
                1,
                now,
                expires,
                maximumStaleUntil,
                recheckAt,
                RuntimeLeaseStatus.ACTIVE,
                1);
        return repository.insert(draft, request.correlationId());
    }

    @Override
    public RuntimeAuthorizationLease check(RuntimeAuthorizationCheckpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        RuntimeAuthorizationLease current = find(checkpoint.tenantId(), checkpoint.leaseId());
        Instant now = clock.instant();
        if (terminal(current.status())) return current;
        if (checkpoint.presentedFencingVersion() != current.fencingVersion()) {
            return transition(
                    current,
                    RuntimeLeaseStatus.FENCED,
                    current.issuedEpoch(),
                    current.maximumStaleUntil(),
                    current.recheckAfter(),
                    "RUNTIME_FENCING_VERSION_MISMATCH",
                    checkpoint.correlationId(),
                    now);
        }
        if (!now.isBefore(current.expiresAt())) {
            return transition(
                    current,
                    RuntimeLeaseStatus.EXPIRED,
                    current.issuedEpoch(),
                    current.maximumStaleUntil(),
                    current.recheckAfter(),
                    ResourceDecisionReasonCodes.RUNTIME_AUTHORIZATION_LEASE_EXPIRED,
                    checkpoint.correlationId(),
                    now);
        }
        if (now.isAfter(current.maximumStaleUntil())) {
            return transition(
                    current,
                    RuntimeLeaseStatus.FENCED,
                    current.issuedEpoch(),
                    current.maximumStaleUntil(),
                    current.recheckAfter(),
                    ResourceDecisionReasonCodes.RUNTIME_MAXIMUM_STALE_WINDOW_EXCEEDED,
                    checkpoint.correlationId(),
                    now);
        }

        PolicyVersion latestPolicy = evidence.currentPolicyVersion(current.resourceRef().tenantId());
        if (!current.issuedPolicyVersion().equals(latestPolicy)) {
            return transition(
                    current,
                    RuntimeLeaseStatus.FENCED,
                    current.issuedEpoch(),
                    current.maximumStaleUntil(),
                    current.recheckAfter(),
                    "RUNTIME_POLICY_VERSION_MISMATCH",
                    checkpoint.correlationId(),
                    now);
        }
        SecurityEpoch latest = evidence.currentSecurityEpoch(
                current.resourceRef().tenantId(), current.principal(), current.resourceRef());
        if (!current.issuedEpoch().equals(latest)) {
            return transition(
                    current,
                    RuntimeLeaseStatus.FENCED,
                    latest,
                    current.maximumStaleUntil(),
                    current.recheckAfter(),
                    ResourceDecisionReasonCodes.RUNTIME_EPOCH_MISMATCH,
                    checkpoint.correlationId(),
                    now);
        }

        RuntimeAuthorizationLease checked = current;
        if (!now.isBefore(current.recheckAfter())) {
            Duration hardStaleWindow = riskRecheck(current);
            Instant nextMaximumStale = minimum(now.plus(hardStaleWindow), current.expiresAt());
            Instant nextRecheck = minimum(now.plus(half(hardStaleWindow)), nextMaximumStale);
            checked = transition(
                    current,
                    RuntimeLeaseStatus.ACTIVE,
                    latest,
                    nextMaximumStale,
                    nextRecheck,
                    "RUNTIME_AUTHORIZATION_RECHECKED",
                    checkpoint.correlationId(),
                    now);
        }
        repository.recordCheckpoint(
                checked,
                checkpoint.operationPhase(),
                "RUNTIME_AUTHORIZATION_CHECKPOINT_OK",
                checkpoint.correlationId(),
                now);
        return checked;
    }

    @Override
    public RuntimeAuthorizationLease complete(String tenantId, String leaseId, String correlationId) {
        RuntimeAuthorizationLease current = find(tenantId, leaseId);
        if (current.status() == RuntimeLeaseStatus.COMPLETED) return current;
        if (terminal(current.status())) throw new IllegalStateException("RUNTIME_LEASE_NOT_COMPLETABLE:" + current.status());
        return transition(
                current,
                RuntimeLeaseStatus.COMPLETED,
                current.issuedEpoch(),
                current.maximumStaleUntil(),
                current.recheckAfter(),
                "RUNTIME_AUTHORIZATION_COMPLETED",
                required(correlationId, "correlationId"),
                clock.instant());
    }

    @Override
    public RuntimeAuthorizationLease revoke(String tenantId, String leaseId, String reasonCode, String correlationId) {
        RuntimeAuthorizationLease current = find(tenantId, leaseId);
        if (current.status() == RuntimeLeaseStatus.REVOKED || current.status() == RuntimeLeaseStatus.FENCED) return current;
        if (current.status() == RuntimeLeaseStatus.COMPLETED || current.status() == RuntimeLeaseStatus.EXPIRED) {
            throw new IllegalStateException("RUNTIME_LEASE_TERMINAL:" + current.status());
        }
        return transition(
                current,
                RuntimeLeaseStatus.REVOKED,
                current.issuedEpoch(),
                current.maximumStaleUntil(),
                current.recheckAfter(),
                required(reasonCode, "reasonCode"),
                required(correlationId, "correlationId"),
                clock.instant());
    }

    private RuntimeAuthorizationLease transition(
            RuntimeAuthorizationLease current,
            RuntimeLeaseStatus target,
            SecurityEpoch epoch,
            Instant maximumStaleUntil,
            Instant recheck,
            String reason,
            String correlation,
            Instant at) {
        return repository.update(current, target, epoch, maximumStaleUntil, recheck, reason, correlation, at);
    }

    private RuntimeAuthorizationLease find(String tenant, String id) {
        return repository.find(required(tenant, "tenantId"), required(id, "leaseId"))
                .orElseThrow(() -> new IllegalArgumentException("Runtime authorization lease not found"));
    }

    private Duration riskRecheck(AuthorizationRequest request, AuthorizationDecision decision) {
        return request.action().kind() == ResourceAction.ActionKind.DOWNLOAD
                        || request.action().kind() == ResourceAction.ActionKind.EXPORT
                        || request.action().sideEffecting()
                        || decision.grantedVisibility() == VisibilityLevel.SENSITIVE
                        || decision.grantedVisibility() == VisibilityLevel.SECRET_METADATA
                ? restrictedRecheck
                : readRecheck;
    }

    private Duration riskRecheck(RuntimeAuthorizationLease lease) {
        String permission = lease.permissionCode().toLowerCase(java.util.Locale.ROOT);
        return permission.contains("download")
                        || permission.contains("export")
                        || permission.contains("write")
                        || permission.contains("update")
                        || permission.contains("execute")
                ? restrictedRecheck
                : readRecheck;
    }

    private static boolean terminal(RuntimeLeaseStatus status) {
        return status == RuntimeLeaseStatus.REVOKED
                || status == RuntimeLeaseStatus.EXPIRED
                || status == RuntimeLeaseStatus.FENCED
                || status == RuntimeLeaseStatus.COMPLETED;
    }

    private static Integer parseAttempt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("attemptNo must be numeric", exception);
        }
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static Duration half(Duration value) {
        Duration half = value.dividedBy(2);
        return half.isZero() ? Duration.ofMillis(1) : half;
    }

    private static Instant minimum(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
