package com.opensocket.aievent.core.uiaccessrequest.application;

import static com.opensocket.aievent.core.uiaccessrequest.application.GovernedAccessRequestException.Code.*;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.*;
import com.opensocket.aievent.core.uicapability.core.ResolvedUiAction;
import com.opensocket.aievent.core.uicapability.core.UiActionCatalogResolver;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Converts a server-owned uiActionId into a canonical temporary Scope Grant lifecycle.
 * It never accepts permission, tenant, resource type, principal or scope semantics from browser payloads.
 */
public final class GovernedAccessRequestService {
    private static final long STANDARD_MAX_HOURS = 720;
    private static final long HIGH_RISK_MAX_HOURS = 168;
    private final UiActionCatalogResolver actions;
    private final AuthoritativeResourceDescriptorService descriptors;
    private final ResourceScopeGrantService grants;
    private final GovernedAccessRequestRepository requests;
    private final ResourceAuthorizationPort authorization;

    public GovernedAccessRequestService(UiActionCatalogResolver actions,
                                        AuthoritativeResourceDescriptorService descriptors,
                                        ResourceScopeGrantService grants,
                                        GovernedAccessRequestRepository requests,
                                        ResourceAuthorizationPort authorization) {
        this.actions = Objects.requireNonNull(actions);
        this.descriptors = Objects.requireNonNull(descriptors);
        this.grants = Objects.requireNonNull(grants);
        this.requests = Objects.requireNonNull(requests);
        this.authorization = Objects.requireNonNull(authorization);
    }

    public GovernedAccessRequestResult submit(SubmitGovernedAccessRequestCommand command) {
        AuthenticationContext auth = requireTenantUser(command.authentication());
        String tenantId = auth.activeTenant().tenantId();
        String requesterId = auth.principal().principalId();
        return requests.findByIdempotencyKey(tenantId, command.idempotencyKey())
                .map(existing -> new GovernedAccessRequestResult(existing, grants.find(tenantId, existing.scopeGrantId())))
                .orElseGet(() -> createAndSubmit(command, auth, tenantId, requesterId));
    }

    public GovernedAccessRequestResult findForRequester(AuthenticationContext authentication, String requestId) {
        AuthenticationContext auth = requireTenantUser(authentication);
        GovernedAccessRequestRecord request = requireRequest(auth.activeTenant().tenantId(), requestId);
        if (!request.requesterId().equals(auth.principal().principalId()))
            throw new GovernedAccessRequestException(ACCESS_REQUEST_NOT_FOUND, "Access request not found");
        return new GovernedAccessRequestResult(request, grants.find(request.tenantId(), request.scopeGrantId()));
    }


    public GovernedAccessRequestResult findForReviewer(AuthenticationContext authentication, String requestId,
                                                        String correlationId, Instant requestedAt) {
        AuthenticationContext auth = requireTenantUser(authentication);
        GovernedAccessRequestRecord request = requireRequest(auth.activeTenant().tenantId(), requestId);
        authorizeApprover(auth, request, correlationId, requestedAt);
        return new GovernedAccessRequestResult(request, grants.find(request.tenantId(), request.scopeGrantId()));
    }

    public GovernedAccessRequestResult approve(ReviewGovernedAccessRequestCommand command) {
        AuthenticationContext auth = requireTenantUser(command.authentication());
        String tenantId = auth.activeTenant().tenantId();
        GovernedAccessRequestRecord request = requireRequest(tenantId, command.requestId());
        validateReviewVersions(request, command);
        if (request.state() != GovernedAccessRequestState.PENDING_APPROVAL)
            throw new GovernedAccessRequestException(ACCESS_REQUEST_INVALID_STATE, "Access request is not pending approval");
        if (request.requesterId().equals(auth.principal().principalId()))
            throw new GovernedAccessRequestException(ACCESS_REQUEST_SEPARATION_OF_DUTIES, "Requester cannot approve the same access request");

        ResolvedUiAction action = requireRequestableAction(request.uiActionId());
        ScopeGrantRecord grant = grants.find(tenantId, request.scopeGrantId());
        validateGrantBinding(request, grant, action);
        ResourceDescriptor descriptor = resolveDescriptor(request.resourceRef(), command.correlationId(), command.requestedAt());
        if (descriptor.resourceVersion() != request.resourceVersionAtRequest()
                || descriptor.resourceVersion() != command.expectedResourceVersion())
            throw new GovernedAccessRequestException(ACCESS_REQUEST_RESOURCE_VERSION_STALE, "Resource changed after the request was submitted");
        validateTemporalPolicy(request.validFrom(), request.validTo(), action.definition().riskLane(), command.requestedAt());
        authorizeApprover(auth, request, command.correlationId(), command.requestedAt());

        ScopeGrantRecord active = grants.approve(new ScopeGrantMutationCommand(tenantId, grant.grantId(),
                command.expectedGrantVersion(), auth.principal().principalId(), command.reason(),
                command.correlationId(), command.idempotencyKey() + ":grant", command.requestedAt()));
        GovernedAccessRequestRecord updated = requests.transition(request, GovernedAccessRequestState.ACTIVE,
                auth.principal().principalId(), auth.principal().principalId(), command.reason(),
                command.correlationId(), command.idempotencyKey() + ":request", command.requestedAt());
        return new GovernedAccessRequestResult(updated, active);
    }

    public GovernedAccessRequestResult reject(ReviewGovernedAccessRequestCommand command) {
        AuthenticationContext auth = requireTenantUser(command.authentication());
        String tenantId = auth.activeTenant().tenantId();
        GovernedAccessRequestRecord request = requireRequest(tenantId, command.requestId());
        validateReviewVersions(request, command);
        if (request.state() != GovernedAccessRequestState.PENDING_APPROVAL)
            throw new GovernedAccessRequestException(ACCESS_REQUEST_INVALID_STATE, "Access request is not pending approval");
        if (request.requesterId().equals(auth.principal().principalId()))
            throw new GovernedAccessRequestException(ACCESS_REQUEST_SEPARATION_OF_DUTIES, "Requester cannot reject the same access request");
        authorizeApprover(auth, request, command.correlationId(), command.requestedAt());
        ScopeGrantRecord grant = grants.find(tenantId, request.scopeGrantId());
        ScopeGrantRecord revoked = grants.revoke(new ScopeGrantMutationCommand(tenantId, grant.grantId(),
                command.expectedGrantVersion(), auth.principal().principalId(), command.reason(),
                command.correlationId(), command.idempotencyKey() + ":grant", command.requestedAt()));
        GovernedAccessRequestRecord updated = requests.transition(request, GovernedAccessRequestState.REJECTED,
                auth.principal().principalId(), auth.principal().principalId(), command.reason(),
                command.correlationId(), command.idempotencyKey() + ":request", command.requestedAt());
        return new GovernedAccessRequestResult(updated, revoked);
    }

    private GovernedAccessRequestResult createAndSubmit(SubmitGovernedAccessRequestCommand command,
                                                         AuthenticationContext auth,
                                                         String tenantId,
                                                         String requesterId) {
        ResolvedUiAction action = requireRequestableAction(command.uiActionId());
        ResourceRef resourceRef = new ResourceRef(tenantId, action.resourceType(), command.resourceId());
        ResourceDescriptor descriptor = resolveDescriptor(resourceRef, command.correlationId(), command.requestedAt());
        if (descriptor.resourceVersion() != command.expectedResourceVersion())
            throw new GovernedAccessRequestException(ACCESS_REQUEST_RESOURCE_VERSION_STALE, "Resource version changed before request submission");
        validateVisibility(command.requestedVisibility(), action.definition().requestedVisibility());
        validateDuration(command.durationHours(), action.definition().riskLane());

        Instant validFrom = command.requestedAt();
        Instant validTo = validFrom.plusSeconds(Math.multiplyExact(command.durationHours(), 3600L));
        String requestId = deterministicId("access-request", tenantId, command.idempotencyKey());
        String grantId = deterministicId("access-grant", tenantId, command.idempotencyKey());
        ScopeGrantRecord draftGrant = grants.create(new CreateScopeGrantCommand(tenantId, grantId,
                ScopePrincipalType.USER, requesterId, action.resourceAction().permissionCode(), action.resourceType(),
                ScopeType.RESOURCE, command.resourceId(), command.requestedVisibility(), validFrom, validTo,
                ScopeGrantSource.ACCESS_REQUEST, command.businessPurpose(), requesterId, command.correlationId(),
                command.idempotencyKey() + ":grant:create", command.requestedAt()));
        GovernedAccessRequestRecord draft = requests.insert(new GovernedAccessRequestRecord(tenantId, requestId,
                grantId, action.definition().uiActionId(), action.resourceType(), command.resourceId(),
                descriptor.resourceVersion(), command.requestedVisibility(), requesterId, command.businessPurpose(),
                validFrom, validTo, GovernedAccessRequestState.DRAFT, "", command.idempotencyKey(), 1,
                command.requestedAt(), command.requestedAt()), command.correlationId());
        ScopeGrantRecord pendingGrant = grants.submit(new ScopeGrantMutationCommand(tenantId, grantId,
                draftGrant.version(), requesterId, "ACCESS_REQUEST_SUBMITTED", command.correlationId(),
                command.idempotencyKey() + ":grant:submit", command.requestedAt()));
        GovernedAccessRequestRecord pending = requests.transition(draft, GovernedAccessRequestState.PENDING_APPROVAL,
                "", requesterId, "ACCESS_REQUEST_SUBMITTED", command.correlationId(),
                command.idempotencyKey() + ":request:submit", command.requestedAt());
        return new GovernedAccessRequestResult(pending, pendingGrant);
    }

    private ResolvedUiAction requireRequestableAction(String uiActionId) {
        ResolvedUiAction action = actions.resolve(uiActionId)
                .orElseThrow(() -> new GovernedAccessRequestException(ACCESS_REQUEST_ACTION_NOT_REQUESTABLE, "Requested action is not available"));
        String profile = action.definition().requestAccessProfile();
        if (!"ACTIVE".equals(action.definition().lifecycle()) || profile.isBlank() || "NONE".equalsIgnoreCase(profile)
                || !action.resourceAction().sideEffecting() && action.definition().requestedVisibility() == VisibilityLevel.NONE)
            throw new GovernedAccessRequestException(ACCESS_REQUEST_ACTION_NOT_REQUESTABLE, "Requested action does not support access requests");
        return action;
    }

    private ResourceDescriptor resolveDescriptor(ResourceRef resourceRef, String correlationId, Instant requestedAt) {
        try {
            return descriptors.resolve(resourceRef, new DescriptorResolutionContext(correlationId, "ui-access-request", requestedAt));
        } catch (IllegalArgumentException missing) {
            throw new GovernedAccessRequestException(ACCESS_REQUEST_RESOURCE_NOT_FOUND, "Resource not found or unavailable");
        }
    }

    private void validateGrantBinding(GovernedAccessRequestRecord request, ScopeGrantRecord grant, ResolvedUiAction action) {
        boolean valid = grant.grantSource() == ScopeGrantSource.ACCESS_REQUEST
                && grant.principalType() == ScopePrincipalType.USER
                && grant.principalId().equals(request.requesterId())
                && grant.permissionCode().equals(action.resourceAction().permissionCode())
                && grant.resourceType() == request.resourceType()
                && grant.scopeType() == ScopeType.RESOURCE
                && grant.scopeRefId().equals(request.resourceId())
                && grant.visibilityLevel() == request.requestedVisibility()
                && Objects.equals(grant.validFrom(), request.validFrom())
                && Objects.equals(grant.validTo(), request.validTo());
        if (!valid) throw new GovernedAccessRequestException(ACCESS_REQUEST_GRANT_DRIFT, "Access request and Scope Grant no longer match");
    }

    private void authorizeApprover(AuthenticationContext auth, GovernedAccessRequestRecord request,
                                   String correlationId, Instant requestedAt) {
        com.opensocket.aievent.core.iam.security.contract.SecurityEpoch epoch = auth.securityEpoch();
        AuthorizationDecision decision = authorization.evaluate(new AuthorizationRequest(auth.principal(), auth,
                auth.activeTenant(), new ResourceAction("resource.scope.approve", ResourceAction.ActionKind.APPROVE, true),
                request.resourceRef(), request.requestedVisibility(), RequestChannel.UI,
                "Approve governed temporary access request", OperationPhase.BEFORE_SIDE_EFFECT, "",
                correlationId, new SecurityEpoch(epoch.globalEpoch(), epoch.tenantEpoch(), epoch.principalEpoch(), 0, 0, 0),
                Map.of("accessRequestId", request.requestId(), "uiActionId", request.uiActionId())));
        if (decision.mode() != AuthorizationDecisionMode.FORMAL || decision.shadowOnly()
                || decision.effect() != DecisionEffect.ALLOW
                || decision.grantedVisibility().ordinal() < request.requestedVisibility().ordinal())
            throw new GovernedAccessRequestException(ACCESS_REQUEST_APPROVER_NOT_AUTHORIZED, "Approver is not authorized for this resource scope");
    }

    private static void validateVisibility(VisibilityLevel requested, VisibilityLevel maximum) {
        if (requested == VisibilityLevel.NONE || requested.ordinal() > maximum.ordinal())
            throw new GovernedAccessRequestException(ACCESS_REQUEST_VISIBILITY_NOT_ALLOWED, "Requested visibility exceeds the action profile");
    }

    private static void validateDuration(long hours, String riskLane) {
        long maximum = highRisk(riskLane) ? HIGH_RISK_MAX_HOURS : STANDARD_MAX_HOURS;
        if (hours < 1 || hours > maximum)
            throw new GovernedAccessRequestException(ACCESS_REQUEST_DURATION_NOT_ALLOWED, "Requested duration exceeds the action risk profile");
    }

    private static void validateTemporalPolicy(Instant validFrom, Instant validTo, String riskLane, Instant now) {
        if (!now.isBefore(validTo))
            throw new GovernedAccessRequestException(ACCESS_REQUEST_DURATION_NOT_ALLOWED, "Access request expired before approval");
        long hours = Math.max(1, java.time.Duration.between(validFrom, validTo).toHours());
        validateDuration(hours, riskLane);
    }

    private static boolean highRisk(String riskLane) {
        return "HIGH".equalsIgnoreCase(riskLane) || "CRITICAL".equalsIgnoreCase(riskLane);
    }

    private GovernedAccessRequestRecord requireRequest(String tenantId, String requestId) {
        return requests.find(tenantId, requestId)
                .orElseThrow(() -> new GovernedAccessRequestException(ACCESS_REQUEST_NOT_FOUND, "Access request not found"));
    }

    private static void validateReviewVersions(GovernedAccessRequestRecord request, ReviewGovernedAccessRequestCommand command) {
        if (request.version() != command.expectedRequestVersion())
            throw new GovernedAccessRequestException(ACCESS_REQUEST_VERSION_CONFLICT, "Access request version changed");
    }

    private static AuthenticationContext requireTenantUser(AuthenticationContext authentication) {
        if (authentication.activeTenant().scope() != TenantRef.Scope.TENANT
                || authentication.principal().principalType() != PrincipalRef.PrincipalType.USER)
            throw new GovernedAccessRequestException(ACCESS_REQUEST_ACTION_NOT_REQUESTABLE, "A tenant user session is required");
        return authentication;
    }

    private static String deterministicId(String prefix, String tenantId, String idempotencyKey) {
        UUID value = UUID.nameUUIDFromBytes((tenantId + ":" + idempotencyKey + ":" + prefix).getBytes(StandardCharsets.UTF_8));
        return prefix + "-" + value;
    }
}
