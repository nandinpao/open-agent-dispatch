package com.opensocket.aievent.core.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.integration.issue.IssueProjectionCommand;
import com.opensocket.aievent.core.integration.issue.IssueProjectionResult;
import com.opensocket.aievent.core.integration.identity.IntegrationConnection;
import com.opensocket.aievent.core.integration.identity.IntegrationConnectionStatus;
import com.opensocket.aievent.core.integration.identity.IntegrationIdentityRepository;
import com.opensocket.aievent.core.integration.identity.IntegrationProjectMapping;
import com.opensocket.aievent.core.integration.identity.ProjectMappingLifecycle;
import com.opensocket.aievent.core.integration.identity.ProjectMappingStatus;
import com.opensocket.aievent.core.integration.issue.projection.IssueProjectionMaterializationService;
import com.opensocket.aievent.core.integration.issue.projection.IssueProjectionReconciliationCase;
import com.opensocket.aievent.core.integration.issue.projection.IssueProjectionReconciliationStatus;
import com.opensocket.aievent.core.integration.issue.projection.IssueProjectionState;
import com.opensocket.aievent.core.integration.issue.projection.IssueProjectionStateService;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyAutomationStatus;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyBindingStatus;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyDecision;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyDecisionOutcome;
import com.opensocket.aievent.core.integration.issue.policy.IssuePolicyDecisionRepository;
import com.opensocket.aievent.core.issuetracking.application.ProjectionIntentPreviewCommand;
import com.opensocket.aievent.core.issuetracking.application.ProjectionIntentPreviewService;
import com.opensocket.aievent.core.issuetracking.contract.ProjectionIntentPreview;
import com.opensocket.aievent.core.issuetracking.contract.ProjectionLifecycleStatus;
import com.opensocket.aievent.core.issuetracking.contract.ProjectionPurpose;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;

/** Governed Projection API. It cannot mutate Task, A2A, Assignment or Result authority. */
@RestController
@RequestMapping("/api/issue-projections")
public class IssueProjectionStateController {
    private final IssueProjectionStateService service;
    private final IssueProjectionMaterializationService materialization;
    private final ProjectionIntentPreviewService previews;
    private final IssuePolicyDecisionRepository policyDecisions;
    private final IntegrationIdentityRepository identities;
    @Autowired(required=false) private ScopedBusinessResourceAccessCoordinator scopedAccess;

    public IssueProjectionStateController(IssueProjectionStateService service,
            IssueProjectionMaterializationService materialization,
            ProjectionIntentPreviewService previews,
            IssuePolicyDecisionRepository policyDecisions,
            IntegrationIdentityRepository identities) {
        this.service=service; this.materialization=materialization; this.previews=previews; this.policyDecisions=policyDecisions; this.identities=identities;
    }

    @GetMapping public List<IssueProjectionState> list(@RequestParam(required=false) ProjectionLifecycleStatus state,@RequestParam(defaultValue="200") int limit){return run(()->service.list(tenant(),state,limit).stream().filter(this::canRead).toList());}
    @GetMapping("/{projectionId}") public IssueProjectionState get(@PathVariable String projectionId){return run(()->authorizeProjection(service.get(tenant(),projectionId),false));}

    @PostMapping("/intent-preview")
    public ProjectionIntentPreview preview(@RequestBody ProjectionIntentPreviewCommand body) {
        return run(() -> { authorizeTask(body==null?null:body.taskId(),"task.read",ResourceAction.ActionKind.READ,false,"RS5_ISSUE_PROJECTION_PREVIEW"); authorizeMapping(body==null?null:body.projectMappingId(),"RS5_ISSUE_PROJECTION_PREVIEW_MAPPING"); return previews.preview(withTenant(body)); });
    }

    @PostMapping("/intents")
    public IssueProjectionState requestIntent(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody IntentRequest body) {
        throw legacyProjectionRetired();
    }

    @PostMapping("/{projectionId}/materialize") public IssueProjectionResult materialize(@PathVariable String projectionId,@RequestHeader("Idempotency-Key") String idempotencyKey,@RequestBody IssueProjectionCommand body){
        throw legacyProjectionRetired();
    }
    @PostMapping("/{projectionId}/retry") public IssueProjectionState retry(@PathVariable String projectionId,@RequestBody GovernanceRequest body){
        throw legacyProjectionRetired();
    }
    @PostMapping("/{projectionId}/suspend") public IssueProjectionState suspend(@PathVariable String projectionId,@RequestBody GovernanceRequest body){
        throw legacyProjectionRetired();
    }
    @PostMapping("/{projectionId}/supersede") public IssueProjectionState supersede(@PathVariable String projectionId,@RequestBody SupersedeRequest body){
        throw legacyProjectionRetired();
    }
    @GetMapping("/reconciliation-cases") public List<IssueProjectionReconciliationCase> cases(@RequestParam(required=false) IssueProjectionReconciliationStatus status,@RequestParam(defaultValue="200") int limit){return run(()->service.cases(tenant(),status,limit));}
    @PostMapping("/reconciliation-cases/{caseId}/resolve") public IssueProjectionReconciliationCase resolve(@PathVariable String caseId,@RequestBody GovernanceRequest body){
        throw legacyProjectionRetired();
    }


    private ResponseStatusException legacyProjectionRetired() {
        return new ResponseStatusException(HttpStatus.GONE,
                "LEGACY_EXTERNAL_ISSUE_PROJECTION_AUTHORITY_RETIRED: Desired-state Issue projection is retired. Use the canonical Agent Issue Connector for explicit Redmine operations.");
    }

    private boolean canRead(IssueProjectionState state){try{authorizeProjection(state,false);return true;}catch(RuntimeException denied){return false;}}
    private IssueProjectionState authorizeProjection(IssueProjectionState state,boolean write){
        if(state==null||scopedAccess==null)return state;
        authorizeMapping(state.projectMappingId(),write?"RS5_ISSUE_PROJECTION_MUTATE_MAPPING":"RS5_ISSUE_PROJECTION_READ_MAPPING");
        if(state.taskIssueLinkId()!=null&&!state.taskIssueLinkId().isBlank()){
            scopedAccess.authorize(ResourceType.TASK_ISSUE_LINK,state.taskIssueLinkId(),write?"integration.issue.link.update":"integration.issue.link.read",write?ResourceAction.ActionKind.UPDATE:ResourceAction.ActionKind.READ,write,VisibilityLevel.SENSITIVE,write?"RS5_ISSUE_PROJECTION_MUTATE":"RS5_ISSUE_PROJECTION_READ");
        } else authorizeTask(state.taskId(),write?"task.update":"task.read",write?ResourceAction.ActionKind.UPDATE:ResourceAction.ActionKind.READ,write,write?"RS5_ISSUE_PROJECTION_TASK_MUTATE":"RS5_ISSUE_PROJECTION_TASK_READ");
        return state;
    }
    private void authorizeTask(String taskId,String permission,ResourceAction.ActionKind kind,boolean sideEffect,String purpose){
        if(scopedAccess==null)return;
        scopedAccess.authorize(ResourceType.TASK,required(taskId,"taskId"),permission,kind,sideEffect,VisibilityLevel.SENSITIVE,purpose);
    }
    private void authorizeMapping(String mappingId,String purpose){
        if(scopedAccess==null)return;
        scopedAccess.authorize(ResourceType.ISSUE_PROJECT_MAPPING,required(mappingId,"projectMappingId"),"integration.issue.mapping.read",ResourceAction.ActionKind.READ,false,VisibilityLevel.SENSITIVE,purpose);
    }

    private IssuePolicyDecision policyForProjectionOverride(String taskId,ProjectionPurpose purpose){
        String resolvedPurpose=(purpose==null?ProjectionPurpose.PRIMARY_ISSUE:purpose).name();
        IssuePolicyDecision decision=policyDecisions.findByTaskAndPurpose(tenant(),required(taskId,"taskId"),resolvedPurpose)
                .orElseThrow(()->new IllegalStateException("ISSUE_POLICY_DECISION_REQUIRED"));
        if(decision.decision()==IssuePolicyDecisionOutcome.NOT_REQUIRED)
            throw new IllegalStateException("ISSUE_POLICY_NOT_REQUIRED");
        return decision;
    }
    private void validatePolicyBindingOverride(IssuePolicyDecision decision,String connectionId,String mappingId){
        if(decision.bindingStatus()!=IssuePolicyBindingStatus.RESOLVED)return;
        if(decision.connectionId()!=null&&!decision.connectionId().equals(required(connectionId,"connectionId")))
            throw new IllegalStateException("ISSUE_POLICY_CONNECTION_OVERRIDE_CONFLICT");
        if(decision.projectMappingId()!=null&&!decision.projectMappingId().equals(required(mappingId,"projectMappingId")))
            throw new IllegalStateException("ISSUE_POLICY_MAPPING_OVERRIDE_CONFLICT");
    }
    private GovernedOverrideBinding governedOverrideBinding(String connectionId,String mappingId){
        String tenant=tenant();
        String requestedConnection=required(connectionId,"connectionId");
        IntegrationProjectMapping mapping=identities.findMapping(tenant,required(mappingId,"projectMappingId"))
                .orElseThrow(()->new IllegalStateException("ISSUE_POLICY_OVERRIDE_MAPPING_NOT_FOUND"));
        if(!mapping.enabled()||mapping.lifecycleStatus()!=ProjectMappingLifecycle.ACTIVE||mapping.mappingStatus()!=ProjectMappingStatus.VALID)
            throw new IllegalStateException("ISSUE_POLICY_OVERRIDE_MAPPING_NOT_ACTIVE");
        if(!requestedConnection.equals(required(mapping.connectionId(),"mapping.connectionId")))
            throw new IllegalStateException("ISSUE_POLICY_OVERRIDE_CONNECTION_BINDING_MISMATCH");
        IntegrationConnection connection=identities.findConnection(tenant,requestedConnection)
                .orElseThrow(()->new IllegalStateException("ISSUE_POLICY_OVERRIDE_CONNECTION_NOT_FOUND"));
        if(!connection.enabled()||connection.status()!=IntegrationConnectionStatus.ACTIVE)
            throw new IllegalStateException("ISSUE_POLICY_OVERRIDE_CONNECTION_NOT_ACTIVE");
        String schemaHash=normalized(mapping.metadataSchemaHash());
        if(schemaHash==null){
            var version=identities.findMappingVersion(tenant,mapping.mappingId(),mapping.mappingVersion()).orElse(null);
            if(version!=null) schemaHash=normalized(firstNonblank(version.metadataSchemaHash(),version.configurationHash()));
        }
        if(schemaHash==null) throw new IllegalStateException("ISSUE_POLICY_OVERRIDE_MAPPING_SCHEMA_UNAVAILABLE");
        return new GovernedOverrideBinding(connection,mapping,schemaHash);
    }

    private void recordPolicyOverride(IssuePolicyDecision current,IssueProjectionState projection,String requestCorrelation){
        IssuePolicyDecision updated=new IssuePolicyDecision(current.tenantId(),current.decisionId(),current.taskId(),current.projectionPurpose(),
                current.policyId(),current.policyVersion(),current.taskIssueSyncPolicy(),current.decision(),current.reasonCode(),
                current.sourceEventId(),current.sourceEventType(),current.taskStatus(),IssuePolicyBindingStatus.RESOLVED,
                required(projection.connectionId(),"connectionId"),required(projection.projectMappingId(),"projectMappingId"),projection.projectMappingVersion(),
                required(projection.projectMappingSchemaHash(),"mappingSchemaHash"),projection.projectionId(),projection.taskIssueLinkId(),current.outboxId(),
                current.issueOperation(),current.adapterActionId(),current.actionIdempotencyKey(),current.terminalGeneration(),
                IssuePolicyAutomationStatus.INTENT_READY,null,null,requestCorrelation,current.causationId(),current.traceId(),
                "USER",actor(),current.version()+1,current.createdAt(),java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        policyDecisions.save(updated);
    }

    private ProjectionIntentPreviewCommand withTenant(ProjectionIntentPreviewCommand b){if(b==null)throw bad("body is required.");return new ProjectionIntentPreviewCommand(tenant(),b.taskId(),b.taskType(),b.sourceSystem(),b.connectionId(),b.projectMappingId(),b.projectionPurpose(),b.mappingVersion(),b.mappingSchemaHash(),b.summary(),b.description(),b.issueType(),b.priority(),b.labels(),b.components(),b.approvedContext(),b.comments(),b.links(),b.sourceEvidence(),b.domainEventId(),b.operationSequence(),b.expectedProjectionVersion(),correlation(),b.createdAt());}
    private String normalized(String value){return value==null||value.isBlank()?null:value.trim();}
    private String firstNonblank(String... values){if(values==null)return null;for(String value:values){String v=normalized(value);if(v!=null)return v;}return null;}
    private record GovernedOverrideBinding(IntegrationConnection connection,IntegrationProjectMapping mapping,String schemaHash){}
    private String tenant(){return required(context().tenantId(),"tenantId");} private String actor(){return required(context().operatorId(),"actorId");} private String correlation(){String v=context().correlationId();return v==null||v.isBlank()?java.util.UUID.randomUUID().toString():v.trim();}
    private OpenDispatchRequestContext context(){return OpenDispatchRequestContextHolder.current().orElseThrow(()->bad("Request context is required."));}
    private String required(String v,String name){if(v==null||v.isBlank())throw bad(name+" is required.");return v.trim();}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private <T>T run(Operation<T> op){try{return op.get();}catch(ResponseStatusException e){throw e;}catch(IllegalArgumentException e){if(e.getMessage()!=null&&e.getMessage().contains("not found"))throw new ResponseStatusException(HttpStatus.NOT_FOUND,e.getMessage());throw bad(e.getMessage());}catch(IllegalStateException e){throw new ResponseStatusException(HttpStatus.CONFLICT,e.getMessage());}}
    @FunctionalInterface private interface Operation<T>{T get();}
    public record GovernanceRequest(String reason){}
    public record SupersedeRequest(String replacementProjectionId,String reason){}
    public record IntentRequest(String taskId,String connectionId,String projectMappingId,int mappingVersion,String mappingSchemaHash,ProjectionPurpose projectionPurpose){}
}
