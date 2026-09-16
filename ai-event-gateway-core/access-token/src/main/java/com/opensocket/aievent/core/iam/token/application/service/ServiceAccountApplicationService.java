package com.opensocket.aievent.core.iam.token.application.service;

import com.opensocket.aievent.core.iam.token.application.command.*;
import com.opensocket.aievent.core.iam.token.application.port.in.ServiceAccountCommandPort;
import com.opensocket.aievent.core.iam.token.application.port.out.*;
import com.opensocket.aievent.core.iam.token.domain.*;
import com.opensocket.aievent.core.iam.token.event.TokenDomainEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class ServiceAccountApplicationService implements ServiceAccountCommandPort {
    private final ServiceAccountRepository repository;
    private final ServiceAccountOwnerPort owners;
    private final TokenEventPublisher events;
    private final Clock clock;

    public ServiceAccountApplicationService(ServiceAccountRepository repository, ServiceAccountOwnerPort owners, TokenEventPublisher events, Clock clock) {
        this.repository = repository;
        this.owners = owners;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public ServiceAccount require(String tenantId, String serviceAccountId) {
        return find(tenantId, serviceAccountId);
    }

    @Override
    public ServiceAccount create(CreateServiceAccountCommand c) {
        Instant now = clock.instant();
        owners.requireActiveOwner(c.tenantId(), c.ownerUserId(), c.ownerDepartmentId());
        ServiceAccount a = ServiceAccount.create(
                c.tenantId(), new ServiceAccountId(c.serviceAccountId()), c.name(), c.description(),
                c.ownerUserId(), c.ownerDepartmentId(), c.responsibilityBindingId(), c.restrictions(), c.machineScopes(), c.allowedSourceSystems(),
                c.tokenMaxTtl(), c.maxActiveTokens(), c.credentialMaxTtl(), c.maxActiveCredentials(),
                c.rateLimitPerMinute(), c.nextReviewAt(), c.actorId(), now);
        repository.save(a, 0);
        publish("SERVICE_ACCOUNT_CREATED", a, c.actorId(), c.correlationId(), "", now,
                Map.of("machineScopeCount", Integer.toString(a.machineScopes().size()),
                        "sourceSystemCount", Integer.toString(a.allowedSourceSystems().size())));
        return a;
    }

    @Override
    public ServiceAccount updateMachineBoundary(UpdateServiceAccountMachineBoundaryCommand c) {
        Instant now = clock.instant();
        ServiceAccount old = find(c.tenantId(), c.serviceAccountId());
        owners.requireActiveOwner(c.tenantId(), old.ownerUserId(), old.ownerDepartmentId());
        if (c.expectedVersion() != old.version()) {
            throw new TokenDomainException(TokenReasonCode.OPTIMISTIC_LOCK_CONFLICT, "Service Account version conflict");
        }
        ServiceAccount updated = old.updateMachineBoundary(
                c.responsibilityBindingId(), c.restrictions(), c.machineScopes(), c.allowedSourceSystems(),
                c.credentialMaxTtl(), c.maxActiveCredentials(), c.actorId(), now);
        repository.save(updated, old.version());
        publish("SERVICE_ACCOUNT_MACHINE_BOUNDARY_UPDATED", updated, c.actorId(), c.correlationId(), "", now,
                Map.of("machineScopeCount", Integer.toString(updated.machineScopes().size()),
                        "sourceSystemCount", Integer.toString(updated.allowedSourceSystems().size())));
        return updated;
    }

    @Override
    public ServiceAccount reviewOwnership(ReviewServiceAccountOwnershipCommand c) {
        Instant now = clock.instant();
        owners.requireActiveOwner(c.tenantId(), c.ownerUserId(), c.ownerDepartmentId());
        ServiceAccount old = find(c.tenantId(), c.serviceAccountId());
        ServiceAccount a = old.reviewOwnership(c.ownerUserId(), c.ownerDepartmentId(), c.nextReviewAt(), c.actorId(), now);
        repository.save(a, old.version());
        publish("SERVICE_ACCOUNT_OWNERSHIP_REVIEWED", a, c.actorId(), c.correlationId(), "", now, Map.of());
        return a;
    }

    @Override
    public ServiceAccount reconcileOwnership(ReconcileServiceAccountOwnershipCommand c) {
        Instant now = clock.instant();
        ServiceAccount old = find(c.tenantId(), c.serviceAccountId());
        if (owners.isActiveOwner(c.tenantId(), old.ownerUserId(), old.ownerDepartmentId())) return old;
        if (old.status() == ServiceAccountStatus.OWNERSHIP_REVIEW) return old;
        ServiceAccount a = old.requireOwnershipReview("OWNER_OR_DEPARTMENT_MEMBERSHIP_INACTIVE", c.actorId(), now);
        repository.save(a, old.version());
        publish("SERVICE_ACCOUNT_OWNERSHIP_REVIEW_REQUIRED", a, c.actorId(), c.correlationId(), TokenReasonCode.SERVICE_ACCOUNT_OWNERSHIP_REVIEW_REQUIRED.name(), now, Map.of());
        return a;
    }

    @Override
    public ServiceAccount suspendRisk(SuspendServiceAccountRiskCommand c) {
        Instant now = clock.instant();
        ServiceAccount old = find(c.tenantId(), c.serviceAccountId());
        ServiceAccount a = old.suspendRisk(c.riskLevel(), c.reason(), c.actorId(), now);
        repository.save(a, old.version());
        publish("SERVICE_ACCOUNT_RISK_SUSPENDED", a, c.actorId(), c.correlationId(), TokenReasonCode.SERVICE_ACCOUNT_RISK_SUSPENDED.name(), now, Map.of());
        return a;
    }

    private ServiceAccount find(String tenant, String id) {
        return repository.find(tenant, new ServiceAccountId(id))
                .orElseThrow(() -> new TokenDomainException(TokenReasonCode.SERVICE_ACCOUNT_NOT_FOUND, "Service account not found"));
    }

    private void publish(String type, ServiceAccount a, String actor, String correlation, String reason, Instant at, Map<String,String> metadata) {
        events.publish(new TokenDomainEvent(UUID.randomUUID().toString(), type, a.tenantId(), "SERVICE_ACCOUNT",
                a.serviceAccountId().value(), "", actor, correlation, reason, at, metadata));
    }
}
