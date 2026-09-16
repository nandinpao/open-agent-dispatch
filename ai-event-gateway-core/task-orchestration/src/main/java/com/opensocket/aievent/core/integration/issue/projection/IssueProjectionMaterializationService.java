package com.opensocket.aievent.core.integration.issue.projection;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.integration.issue.IssueProjectionCommand;
import com.opensocket.aievent.core.integration.identity.IntegrationConnection;
import com.opensocket.aievent.core.integration.identity.IntegrationConnectionStatus;
import com.opensocket.aievent.core.integration.identity.IntegrationIdentityRepository;
import com.opensocket.aievent.core.integration.identity.IntegrationProjectMapping;
import com.opensocket.aievent.core.integration.identity.ProjectMappingLifecycle;
import com.opensocket.aievent.core.integration.identity.ProjectMappingStatus;
import com.opensocket.aievent.core.integration.issue.IssueProjectionResult;
import com.opensocket.aievent.core.integration.issue.IssueProjectionService;
import com.opensocket.aievent.core.issuetracking.application.ProjectionIntentPreviewCommand;
import com.opensocket.aievent.core.issuetracking.application.ProjectionIntentPreviewService;
import com.opensocket.aievent.core.issuetracking.contract.ExternalIssueComment;
import com.opensocket.aievent.core.issuetracking.contract.ExternalIssueRelation;
import com.opensocket.aievent.core.issuetracking.contract.ProjectionLifecycleStatus;

/** Materializes provider work only from a validated Canonical ExternalIssueDocument. */
@Service
public class IssueProjectionMaterializationService {
    private final IssueProjectionStateService states;
    private final IssueProjectionService projections;
    private final ProjectionIntentPreviewService previews;
    private final IntegrationIdentityRepository identities;

    public IssueProjectionMaterializationService(IssueProjectionStateService states,
            IssueProjectionService projections, ProjectionIntentPreviewService previews,
            IntegrationIdentityRepository identities) {
        this.states = states; this.projections = projections; this.previews = previews; this.identities = identities;
    }

    @Transactional
    public IssueProjectionResult materialize(String tenantId,String projectionId,IssueProjectionCommand command,
            String requestIdempotencyKey,String correlationId) {
        throw new IllegalStateException("LEGACY_EXTERNAL_ISSUE_PROJECTION_AUTHORITY_RETIRED");
    }

    private GovernedDestination governedDestination(IssueProjectionState state) {
        IntegrationProjectMapping mapping=identities.findMapping(state.tenantId(),required(state.projectMappingId(),"projectMappingId"))
                .orElseThrow(()->new IllegalStateException("ISSUE_PROJECTION_MAPPING_NOT_FOUND"));
        if(!mapping.enabled()||mapping.lifecycleStatus()!=ProjectMappingLifecycle.ACTIVE||mapping.mappingStatus()!=ProjectMappingStatus.VALID)
            throw new IllegalStateException("ISSUE_PROJECTION_MAPPING_NOT_ACTIVE");
        if(!required(mapping.connectionId(),"mapping.connectionId").equals(required(state.connectionId(),"connectionId")))
            throw new IllegalStateException("ISSUE_PROJECTION_CONNECTION_BINDING_MISMATCH");
        if(mapping.mappingVersion()!=state.projectMappingVersion())
            throw new IllegalStateException("ISSUE_PROJECTION_MAPPING_VERSION_STALE");
        String schemaHash=normalized(mapping.metadataSchemaHash());
        if(schemaHash==null){
            var version=identities.findMappingVersion(state.tenantId(),mapping.mappingId(),state.projectMappingVersion()).orElse(null);
            if(version!=null) schemaHash=normalized(first(version.metadataSchemaHash(),version.configurationHash()));
        }
        if(schemaHash==null||!schemaHash.equals(required(state.projectMappingSchemaHash(),"projectMappingSchemaHash")))
            throw new IllegalStateException("ISSUE_PROJECTION_MAPPING_SCHEMA_AUTHORITY_MISMATCH");
        IntegrationConnection connection=identities.findConnection(state.tenantId(),state.connectionId())
                .orElseThrow(()->new IllegalStateException("ISSUE_PROJECTION_CONNECTION_NOT_FOUND"));
        if(!connection.enabled()||connection.status()!=IntegrationConnectionStatus.ACTIVE)
            throw new IllegalStateException("ISSUE_PROJECTION_CONNECTION_NOT_ACTIVE");
        if(normalized(mapping.externalProjectId())==null&&normalized(mapping.externalProjectKey())==null)
            throw new IllegalStateException("ISSUE_PROJECTION_EXTERNAL_PROJECT_BINDING_REQUIRED");
        return new GovernedDestination(connection,mapping);
    }

    private String normalized(String value){return value==null||value.isBlank()?null:value.trim();}
    private String first(String... values){if(values==null)return null;for(String v:values){String n=normalized(v);if(n!=null)return n;}return null;}
    private record GovernedDestination(IntegrationConnection connection,IntegrationProjectMapping mapping){}

    private Map<String,Object> canonicalPayload(com.opensocket.aievent.core.issuetracking.contract.ProjectionIntentPreview preview) {
        var d=preview.document(); var out=new LinkedHashMap<String,Object>();
        out.put("canonicalDocumentSchemaVersion",d.schemaVersion()); out.put("projectionId",d.projectionId());
        out.put("taskReference",Map.of("taskId",d.taskReference().taskId(),"taskType",d.taskReference().taskType(),"sourceSystem",d.taskReference().sourceSystem(),"correlationId",d.taskReference().correlationId()));
        out.put("summary",d.summary()); out.put("description",d.description()); out.put("issueType",d.issueType()); out.put("priority",d.priority());
        out.put("labels",d.labels()); out.put("components",d.components()); out.put("approvedContext",d.approvedContext());
        out.put("comments",List.of()); out.put("links",List.of()); out.put("sourceEvidence",d.sourceEvidence());
        out.put("mappingVersion",d.mappingVersion()); out.put("mappingSchemaHash",d.mappingSchemaHash()); out.put("documentHash",preview.documentHash());
        return Map.copyOf(out);
    }

    private Map<String,Object> approvedContext(Map<String,Object> payload){Object v=payload.get("approvedContext");if(v instanceof Map<?,?> m){var out=new LinkedHashMap<String,Object>();m.forEach((k,x)->out.put(String.valueOf(k),x));return Map.copyOf(out);}return Map.of();}
    private Map<String,String> stringMap(Object value){if(!(value instanceof Map<?,?> m))return Map.of();var out=new LinkedHashMap<String,String>();m.forEach((k,v)->out.put(String.valueOf(k),String.valueOf(v)));return Map.copyOf(out);}
    private List<String> strings(Object value){if(!(value instanceof Iterable<?> values))return List.of();var out=new ArrayList<String>();for(Object v:values)if(v!=null&&!String.valueOf(v).isBlank())out.add(String.valueOf(v));return List.copyOf(out);}
    private String defaultText(Object value,String fallback){String v=text(value);return v==null||v.isBlank()?fallback:v;}
    private String text(Object value){return value==null?null:String.valueOf(value);}
    private String required(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" is required");return value.trim();}
}
