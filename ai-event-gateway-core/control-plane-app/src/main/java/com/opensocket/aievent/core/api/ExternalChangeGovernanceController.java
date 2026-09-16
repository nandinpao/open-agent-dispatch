package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.integration.identity.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.runtime.IntegrationResourceAccessCoordinator;
import com.opensocket.aievent.core.resourceaccess.runtime.ExternalWriteExecutionAuthorization;
import com.opensocket.aievent.core.issuetracking.identity.ProviderExecutionAuthorizationRef;
import com.opensocket.aievent.core.issuetracking.application.change.BidirectionalCommentSyncService;
import com.opensocket.aievent.core.issuetracking.application.change.BidirectionalRelationSyncService;
import com.opensocket.aievent.core.issuetracking.application.change.ExternalChangePolicyService;
import com.opensocket.aievent.core.issuetracking.application.change.ProviderActionCandidateService;
import com.opensocket.aievent.core.issuetracking.change.ExternalChangeDecisionAction;
import com.opensocket.aievent.core.issuetracking.change.ExternalChangeDirection;
import com.opensocket.aievent.core.issuetracking.change.ExternalChangePolicy;
import com.opensocket.aievent.core.issuetracking.change.ExternalChangePolicyStatus;
import com.opensocket.aievent.core.issuetracking.change.ExternalChangeResourceType;
import com.opensocket.aievent.core.issuetracking.change.ExternalCommentSync;
import com.opensocket.aievent.core.issuetracking.change.ExternalRelationSync;
import com.opensocket.aievent.core.issuetracking.change.ExternalSyncStatus;
import com.opensocket.aievent.core.issuetracking.change.ProviderActionCandidate;
import com.opensocket.aievent.core.issuetracking.change.ProviderActionCandidateEvent;
import com.opensocket.aievent.core.issuetracking.change.ProviderActionCandidateStatus;

/** Operator API for Phase 3G external-change policy, collaboration sync and Human Action Candidates. */
@RestController
@RequestMapping("/api/integrations/external-change-governance")
public class ExternalChangeGovernanceController {
    private final ExternalChangePolicyService policies;
    private final BidirectionalCommentSyncService comments;
    private final BidirectionalRelationSyncService relations;
    private final ProviderActionCandidateService candidates;
    private final IntegrationIdentityRepository identities;
    @Autowired(required=false) private IntegrationResourceAccessCoordinator resourceAccess;
    @Value("${resource-access.integration-enabled:false}") private boolean integrationEnabled;
    @Value("${resource-access.external-write-enabled:false}") private boolean externalWriteEnabled;
 @Value("${resource-access.enforcement-mode:OFF}") private ResourceAccessEnforcementMode enforcementMode=ResourceAccessEnforcementMode.OFF;

    public ExternalChangeGovernanceController(ExternalChangePolicyService policies,
            BidirectionalCommentSyncService comments,
            BidirectionalRelationSyncService relations,
            ProviderActionCandidateService candidates, IntegrationIdentityRepository identities) {
        this.policies = policies;
        this.comments = comments;
        this.relations = relations;
        this.candidates = candidates;
        this.identities = identities;
    }

    @GetMapping("/policies")
    public List<ExternalChangePolicy> policies(
            @RequestParam(required=false) ExternalChangePolicyStatus status,
            @RequestParam(defaultValue="200") int limit) {
        return run(() -> policies.list(tenant(), status, limit));
    }

    @PutMapping("/policies/{policyId}")
    public ExternalChangePolicy savePolicy(@PathVariable String policyId,
            @RequestHeader(value="If-Match", required=false) String ifMatch,
            @RequestBody PolicyRequest body) {
        throw legacyBidirectionalAuthorityRetired();
    }

    @PostMapping("/policies/{policyId}/activate")
    public ExternalChangePolicy activate(@PathVariable String policyId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody GovernedReason body) {
        throw legacyBidirectionalAuthorityRetired();
    }

    @PostMapping("/policies/{policyId}/disable")
    public ExternalChangePolicy disable(@PathVariable String policyId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody GovernedReason body) {
        throw legacyBidirectionalAuthorityRetired();
    }

    @GetMapping("/comment-sync")
    public List<ExternalCommentSync> commentSync(
            @RequestParam(required=false) ExternalSyncStatus status,
            @RequestParam(defaultValue="200") int limit) {
        return run(() -> comments.list(tenant(), status, limit));
    }

    @PostMapping("/comment-sync/project")
    public ExternalCommentSync projectComment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CommentProjectionRequest body) {
        throw legacyBidirectionalAuthorityRetired();
    }

    @GetMapping("/relation-sync")
    public List<ExternalRelationSync> relationSync(
            @RequestParam(required=false) ExternalSyncStatus status,
            @RequestParam(defaultValue="200") int limit) {
        return run(() -> relations.list(tenant(), status, limit));
    }

    @PostMapping("/relation-sync/project")
    public ExternalRelationSync projectRelation(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody RelationProjectionRequest body) {
        throw legacyBidirectionalAuthorityRetired();
    }

    @GetMapping("/candidates")
    public List<ProviderActionCandidate> candidates(
            @RequestParam(required=false) ProviderActionCandidateStatus status,
            @RequestParam(defaultValue="200") int limit) {
        return run(() -> candidates.list(tenant(), status, limit));
    }

    @GetMapping("/candidates/{candidateId}/events")
    public List<ProviderActionCandidateEvent> candidateEvents(@PathVariable String candidateId,
            @RequestParam(defaultValue="200") int limit) {
        return run(() -> candidates.events(tenant(), candidateId, limit));
    }

    @PostMapping("/candidates/{candidateId}/review")
    public ProviderActionCandidate review(@PathVariable String candidateId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody CandidateDecisionRequest body) {
        throw legacyBidirectionalAuthorityRetired();
    }

    @PostMapping("/candidates/{candidateId}/approve")
    public ProviderActionCandidate approve(@PathVariable String candidateId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody CandidateDecisionRequest body) {
        throw legacyBidirectionalAuthorityRetired();
    }

    @PostMapping("/candidates/{candidateId}/reject")
    public ProviderActionCandidate reject(@PathVariable String candidateId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody CandidateDecisionRequest body) {
        throw legacyBidirectionalAuthorityRetired();
    }

    @PostMapping("/candidates/{candidateId}/execute")
    public ProviderActionCandidate execute(@PathVariable String candidateId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CandidateDecisionRequest body) {
        throw legacyBidirectionalAuthorityRetired();
    }

    private ResponseStatusException legacyBidirectionalAuthorityRetired() {
        return new ResponseStatusException(HttpStatus.GONE,
                "LEGACY_BIDIRECTIONAL_ISSUE_AUTHORITY_RETIRED: External-change policy, relation/comment projection and Provider Action Candidate mutation are retired. Redmine owns external Issue state; OpenDispatch keeps historical evidence read-only.");
    }

    private ProviderExecutionAuthorizationRef externalWrite(ResourceType resourceType, String resourceId, String mappingId, IntegrationOperation operation,
            String permission, String purpose, String idempotencyKey, boolean sodSatisfied) {
        if (!integrationEnabled || !externalWriteEnabled) return null;
        if (resourceAccess == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Resource Access Integration guard is unavailable.");
        }
        try {
            ExternalWriteExecutionAuthorization authorization = resourceAccess.authorizeExternalWrite(resourceType,
                    required(resourceId,"resourceId"), permission, ResourceAction.ActionKind.UPDATE,
                    VisibilityLevel.SENSITIVE, purpose, required(mappingId,"projectMappingId"), operation,
                    idempotencyKey, sodSatisfied, true);
            if (!authorization.executable()
                    && enforcementMode != ResourceAccessEnforcementMode.OFF
                    && enforcementMode != ResourceAccessEnforcementMode.SHADOW
                    && enforcementMode != ResourceAccessEnforcementMode.READ_ENFORCE) {
                throw new IllegalStateException("EXTERNAL_WRITE_AUTHORIZATION_NOT_EXECUTABLE");
            }
            return providerExecutionRef(authorization);
        } catch (RuntimeException ex) {
            if (enforcementMode == ResourceAccessEnforcementMode.OFF
                    || enforcementMode == ResourceAccessEnforcementMode.SHADOW
                    || enforcementMode == ResourceAccessEnforcementMode.READ_ENFORCE) return null;
            throw ex;
        }
    }
    private ProviderExecutionAuthorizationRef providerExecutionRef(ExternalWriteExecutionAuthorization value) {
        var provider = value.providerIdentity();
        return new ProviderExecutionAuthorizationRef(value.attributionId(), provider.policy(), provider.connectionId(),
                provider.mappingId(), provider.integrationPrincipalId(), provider.credentialId(),
                provider.credentialVersion(), provider.providerActorId());
    }
    private ResourceType resourceType(String taskIssueLinkId) {
        return text(taskIssueLinkId).isBlank() ? ResourceType.ISSUE_PROJECT_MAPPING : ResourceType.TASK_ISSUE_LINK;
    }
    private String permission(String taskIssueLinkId) {
        return text(taskIssueLinkId).isBlank() ? "integration.issue.mapping.update" : "integration.issue.link.update";
    }
    private String resourceId(String taskIssueLinkId,String mappingId) {
        return text(taskIssueLinkId).isBlank() ? required(mappingId,"projectMappingId") : taskIssueLinkId.trim();
    }

    private OpenDispatchRequestContext context() {
        return OpenDispatchRequestContextHolder.current()
                .orElseThrow(() -> bad("Request context is required."));
    }
    private String tenant() { return required(context().tenantId(),"tenantId"); }
    private String operator() { return required(context().operatorId(),"operatorId"); }
    private String correlation() { return text(context().correlationId()); }
    private String required(String value,String name) {
        if (value == null || value.isBlank()) throw bad(name + " is required.");
        return value.trim();
    }
    private String text(String value) { return value == null ? "" : value.trim(); }
    private long parseVersion(String value) {
        String normalized = required(value,"If-Match").replace("W/","").replace("\"","").replace("'","").trim();
        try { long parsed = Long.parseLong(normalized); if (parsed < 1) throw new NumberFormatException(); return parsed; }
        catch (NumberFormatException ex) { throw bad("If-Match must contain a positive row version."); }
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private <T> T run(Operation<T> op) {
        try { return op.get(); }
        catch (ResponseStatusException ex) { throw ex; }
        catch (IllegalArgumentException ex) { throw bad(ex.getMessage()); }
        catch (IllegalStateException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,ex.getMessage()); }
    }
    @FunctionalInterface private interface Operation<T> { T get(); }

    public record PolicyRequest(String name,String connectionId,String projectMappingId,String externalProjectId,
            String issueType,ExternalChangeResourceType resourceType,String fieldPath,ExternalChangeDirection direction,
            ExternalChangeDecisionAction action,IntegrationRiskLevel riskLevel,boolean requiresReauthentication,
            boolean requiresApproval,boolean allowProviderMutation,int priority,ExternalChangePolicyStatus status,
            OffsetDateTime effectiveFrom,OffsetDateTime expiresAt) {}
    public record GovernedReason(String reason) {}
    public record CommentProjectionRequest(String connectionId,String projectMappingId,String externalProjectId,
            String externalIssueId,String taskIssueLinkId,String sourceCommentId,String body) {}
    public record RelationProjectionRequest(String connectionId,String projectMappingId,String externalProjectId,
            String sourceExternalIssueId,String targetExternalIssueId,String sourceTaskIssueLinkId,
            String targetTaskIssueLinkId,String relationType) {}
    public record CandidateDecisionRequest(String reason,String reauthenticationEvidence) {}
}
