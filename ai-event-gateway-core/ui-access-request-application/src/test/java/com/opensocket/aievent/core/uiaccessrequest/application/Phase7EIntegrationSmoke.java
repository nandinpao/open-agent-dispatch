package com.opensocket.aievent.core.uiaccessrequest.application;

import com.opensocket.aievent.core.iam.security.contract.AuthenticationAssurance;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SessionRef;
import com.opensocket.aievent.core.iam.security.contract.SubjectRef;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.*;
import com.opensocket.aievent.core.uicapability.core.CanonicalUiActionCatalogResolver;
import java.time.*;
import java.util.*;

public final class Phase7EIntegrationSmoke {
    private int pass;
    public static void main(String[] args) { new Phase7EIntegrationSmoke().run(); }
    private void run() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        PolicyRepo policyRepo = new PolicyRepo();
        RequestRepo requestRepo = new RequestRepo();
        ResourceScopeGrantService grants = new ResourceScopeGrantService(policyRepo);
        MutableResolver resolver = new MutableResolver(7, now);
        AuthoritativeResourceDescriptorService descriptors = new AuthoritativeResourceDescriptorService(
                new DefaultResourceCatalog(), List.of(resolver));
        ResourceAuthorizationPort authorization = new AllowApproverAuthorization();
        GovernedAccessRequestService service = new GovernedAccessRequestService(new CanonicalUiActionCatalogResolver(),
                descriptors, grants, requestRepo, authorization);
        AuthenticationContext requester = auth("requester", now);
        AuthenticationContext approver = auth("approver", now);

        GovernedAccessRequestResult submitted = service.submit(new SubmitGovernedAccessRequestCommand(requester,
                "task.detail.update", "task-1", 7, VisibilityLevel.STANDARD, 24,
                "Investigate and repair the assigned production task.", "corr-1", "idem-1", now));
        check(submitted.request().state() == GovernedAccessRequestState.PENDING_APPROVAL, "request pending approval");
        check(submitted.grant().state() == ScopeGrantState.PENDING_APPROVAL, "grant pending approval");
        check(submitted.grant().grantSource() == ScopeGrantSource.ACCESS_REQUEST, "grant source access request");
        check("task.update".equals(submitted.grant().permissionCode()), "server action maps canonical permission");
        check(submitted.grant().scopeType() == ScopeType.RESOURCE && "task-1".equals(submitted.grant().scopeRefId()), "server binds resource scope");
        check(service.findForReviewer(approver, submitted.request().requestId(), "corr-review-read", now.plusSeconds(30))
                .request().state() == GovernedAccessRequestState.PENDING_APPROVAL, "authorized reviewer can read pending request");

        expect(GovernedAccessRequestException.Code.ACCESS_REQUEST_SEPARATION_OF_DUTIES, () ->
                service.approve(review(requester, submitted, 7, now.plusSeconds(60), "self-approve")));
        resolver.version = 8;
        expect(GovernedAccessRequestException.Code.ACCESS_REQUEST_RESOURCE_VERSION_STALE, () ->
                service.approve(review(approver, submitted, 7, now.plusSeconds(120), "stale-resource")));
        resolver.version = 7;
        GovernedAccessRequestResult active = service.approve(review(approver, submitted, 7, now.plusSeconds(180), "approve"));
        check(active.request().state() == GovernedAccessRequestState.ACTIVE, "request active");
        check(active.grant().state() == ScopeGrantState.ACTIVE, "grant active");
        check("approver".equals(active.grant().approvedBy()), "independent approver recorded");
        check(service.findForRequester(requester, active.request().requestId()).request().requestId().equals(active.request().requestId()), "requester can read own request");
        expect(GovernedAccessRequestException.Code.ACCESS_REQUEST_NOT_FOUND, () -> service.findForRequester(approver, active.request().requestId()));

        GovernedAccessRequestResult second = service.submit(new SubmitGovernedAccessRequestCommand(requester,
                "task.detail.view", "task-1", 7, VisibilityLevel.SUMMARY, 8,
                "Temporary read access for an independently reviewed support task.", "corr-2", "idem-2", now.plusSeconds(240)));
        GovernedAccessRequestResult rejected = service.reject(review(approver, second, 7, now.plusSeconds(300), "reject"));
        check(rejected.request().state() == GovernedAccessRequestState.REJECTED, "rejected request is terminal");
        check(rejected.grant().state() == ScopeGrantState.REVOKED, "rejected request revokes pending grant");
        System.out.println("PHASE7E_INTEGRATION_SMOKE PASS=" + pass + " FAIL=0");
    }

    private ReviewGovernedAccessRequestCommand review(AuthenticationContext actor, GovernedAccessRequestResult result,
                                                       long resourceVersion, Instant at, String idem) {
        return new ReviewGovernedAccessRequestCommand(actor, result.request().requestId(), result.request().version(),
                result.grant().version(), resourceVersion, "Independent review completed", "corr-review", idem, at);
    }
    private AuthenticationContext auth(String principalId, Instant now) {
        return new AuthenticationContext(new SubjectRef(SubjectRef.IdentityType.HUMAN_USER, principalId),
                new PrincipalRef(PrincipalRef.PrincipalType.USER, principalId), TenantRef.tenant("TENANT-A"),
                Optional.of(new SessionRef("session-" + principalId)), AuthenticationAssurance.passwordOnly(now),
                new com.opensocket.aievent.core.iam.security.contract.SecurityEpoch(1, 1, 1), Optional.empty(),
                now.minusSeconds(60), now.plusSeconds(3600));
    }
    private void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); pass++; }
    private void expect(GovernedAccessRequestException.Code code, Runnable action) {
        try { action.run(); throw new AssertionError("expected " + code); }
        catch (GovernedAccessRequestException error) { if (error.code() != code) throw new AssertionError(error); pass++; }
    }

    private static final class MutableResolver implements ResourceDescriptorResolverPort {
        long version; final Instant now;
        MutableResolver(long version, Instant now) { this.version = version; this.now = now; }
        public boolean supports(ResourceType type) { return type == ResourceType.TASK; }
        public Optional<ResourceDescriptor> resolve(ResourceRef ref, DescriptorResolutionContext context) {
            return Optional.of(new ResourceDescriptor(ref, ref.resourceId(), OwnershipDescriptor.unowned(1), null, null,
                    new VisibilityDescriptor(SensitivityLevel.CONFIDENTIAL, VisibilityLevel.STANDARD, "task-policy", new PolicyVersion(1, 1, "p")),
                    ResourceSecurityState.NORMAL, 1, version, DescriptorAuthority.TASK_DOMAIN, "descriptor-" + version, now));
        }
    }

    private static final class AllowApproverAuthorization implements ResourceAuthorizationPort {
        public AuthorizationDecision evaluate(AuthorizationRequest request) {
            boolean allow = "approver".equals(request.principal().principalId()) && "resource.scope.approve".equals(request.action().permissionCode());
            return new AuthorizationDecision("decision-" + request.principal().principalId(), allow ? DecisionEffect.ALLOW : DecisionEffect.DENY,
                    AuthorizationDecisionMode.FORMAL, request.action().permissionCode(), request.resourceRef(),
                    allow ? request.requestedVisibility() : VisibilityLevel.NONE, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                    new PolicyVersion(1, 1, "p"), SecurityEpoch.ZERO, "descriptor", "", List.of(), Instant.parse("2026-08-01T12:00:00Z"),
                    false, false, Duration.ZERO);
        }
        public AuthorizationDecision explain(AuthorizationRequest request) { return evaluate(request); }
        public AuthorizationDecision simulate(AuthorizationSimulationRequest request) { throw new UnsupportedOperationException(); }
    }

    private static final class RequestRepo implements GovernedAccessRequestRepository {
        final Map<String, GovernedAccessRequestRecord> values = new HashMap<>();
        public Optional<GovernedAccessRequestRecord> find(String tenant, String id) { return Optional.ofNullable(values.get(tenant + ":" + id)); }
        public Optional<GovernedAccessRequestRecord> findByIdempotencyKey(String tenant, String idem) { return values.values().stream().filter(v -> v.tenantId().equals(tenant) && v.idempotencyKey().equals(idem)).findFirst(); }
        public GovernedAccessRequestRecord insert(GovernedAccessRequestRecord value, String correlation) { values.put(value.tenantId() + ":" + value.requestId(), value); return value; }
        public GovernedAccessRequestRecord transition(GovernedAccessRequestRecord current, GovernedAccessRequestState target, String approvedBy, String actor, String reason, String correlation, String idem, Instant at) {
            GovernedAccessRequestRecord value = new GovernedAccessRequestRecord(current.tenantId(), current.requestId(), current.scopeGrantId(), current.uiActionId(), current.resourceType(), current.resourceId(), current.resourceVersionAtRequest(), current.requestedVisibility(), current.requesterId(), current.businessPurpose(), current.validFrom(), current.validTo(), target, approvedBy, current.idempotencyKey(), current.version() + 1, current.createdAt(), at);
            values.put(value.tenantId() + ":" + value.requestId(), value); return value;
        }
    }

    private static final class PolicyRepo implements ResourcePolicyRepository {
        final Map<String, ScopeGrantRecord> grants = new HashMap<>();
        private String key(String tenant, String id) { return tenant + ":" + id; }
        public Optional<ScopeGrantRecord> findScopeGrant(String t, String id) { return Optional.ofNullable(grants.get(key(t, id))); }
        public Optional<ScopeGrantRecord> findScopeGrantByIdempotencyKey(String t, String idem) { return grants.values().stream().filter(v -> v.tenantId().equals(t) && v.idempotencyKey().equals(idem)).findFirst(); }
        public ScopeGrantRecord insertScopeGrant(ScopeGrantRecord g, String c) { grants.put(key(g.tenantId(), g.grantId()), g); return g; }
        public ScopeGrantRecord transitionScopeGrant(ScopeGrantRecord g, ScopeGrantState state, String approved, String actor, String reason, String correlation, String idem, Instant at) { ScopeGrantRecord n = new ScopeGrantRecord(g.tenantId(), g.grantId(), g.principalType(), g.principalId(), g.permissionCode(), g.resourceType(), g.scopeType(), g.scopeRefId(), g.visibilityLevel(), g.validFrom(), g.validTo(), g.grantSource(), g.grantReason(), g.createdBy(), approved.isBlank() ? g.approvedBy() : approved, state, g.idempotencyKey(), g.version() + 1, g.createdAt(), at); grants.put(key(n.tenantId(), n.grantId()), n); return n; }
        public Optional<ExplicitDenyRecord> findExplicitDeny(String t,String i){return Optional.empty();} public Optional<ExplicitDenyRecord> findExplicitDenyByIdempotencyKey(String t,String i){return Optional.empty();} public ExplicitDenyRecord insertExplicitDeny(ExplicitDenyRecord d,String c){throw new UnsupportedOperationException();} public ExplicitDenyRecord transitionExplicitDeny(ExplicitDenyRecord d,ScopeDenyState s,String a,String b,String c,String x,String i,Instant z){throw new UnsupportedOperationException();}
        public Optional<VisibilityPolicyRecord> findVisibilityPolicy(String t,String i){return Optional.empty();} public Optional<VisibilityPolicyRecord> findVisibilityPolicyByIdempotencyKey(String t,String i){return Optional.empty();} public VisibilityPolicyRecord insertVisibilityPolicy(VisibilityPolicyRecord p,String i,String c){throw new UnsupportedOperationException();} public VisibilityPolicyRecord transitionVisibilityPolicy(VisibilityPolicyRecord p,VisibilityPolicyState s,String a,String r,String c,String i,Instant z){throw new UnsupportedOperationException();}
        public Optional<PrincipalClearanceRecord> findPrincipalClearance(String t,String i){return Optional.empty();} public Optional<PrincipalClearanceRecord> findPrincipalClearanceByIdempotencyKey(String t,String i){return Optional.empty();} public Optional<String> findPrincipalClearanceCreator(String t,String i){return Optional.empty();} public PrincipalClearanceRecord insertPrincipalClearance(PrincipalClearanceRecord c,String b,String i,String x){throw new UnsupportedOperationException();} public PrincipalClearanceRecord transitionPrincipalClearance(PrincipalClearanceRecord c,ClearanceStatus s,String p,String a,String r,String x,String i,Instant z){throw new UnsupportedOperationException();}
        public Optional<SecurityStateChangeResult> findSecurityStateChange(ResourceRef r,String i){return Optional.empty();} public SecurityStateChangeResult changeSecurityState(SecurityStateChangeCommand c){throw new UnsupportedOperationException();}
    }
}
