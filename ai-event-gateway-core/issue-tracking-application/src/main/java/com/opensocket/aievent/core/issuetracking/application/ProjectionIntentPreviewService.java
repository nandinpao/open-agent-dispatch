package com.opensocket.aievent.core.issuetracking.application;

import com.opensocket.aievent.core.issuetracking.contract.*;
import com.opensocket.aievent.core.issuetracking.core.ProviderNeutralProjectionValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;


import java.util.Objects;
import org.springframework.stereotype.Service;

/** Builds a provider-neutral preview without persisting or dispatching provider work. */
@Service
public class ProjectionIntentPreviewService {
    private final ProviderNeutralProjectionValidator validator = new ProviderNeutralProjectionValidator();

    public ProjectionIntentPreview preview(ProjectionIntentPreviewCommand command) {
        Objects.requireNonNull(command,"command is required");
        ProjectionPurpose purpose=command.projectionPurpose()==null?ProjectionPurpose.PRIMARY_ISSUE:command.projectionPurpose();
        ProjectionAggregateKey key=new ProjectionAggregateKey(command.tenantId(),command.taskId(),command.connectionId(),command.projectMappingId(),purpose);
        String projectionId=key.stableProjectionId();
        long mappingVersion=command.mappingVersion()<1?1:command.mappingVersion();
        ExternalIssueDocument document=new ExternalIssueDocument(
                2, projectionId,
                new ExternalIssueTaskReference(command.taskId(),command.taskType(),command.sourceSystem(),command.correlationId()),
                command.summary(), command.description(), command.issueType(), command.priority(),
                command.labels(), command.components(), command.approvedContext(), command.comments(), command.links(),
                command.sourceEvidence(), mappingVersion, required(command.mappingSchemaHash(),"mappingSchemaHash"));
        String eventId=required(command.domainEventId(),"domainEventId");
        long sequence=command.operationSequence()<1?1:command.operationSequence();
        OffsetDateTime created=command.createdAt()==null?OffsetDateTime.now(ZoneOffset.UTC):command.createdAt();
        IssueProjectionIntent intent=new IssueProjectionIntent(command.tenantId(),projectionId,key,purpose,command.taskId(),
                command.connectionId(),command.projectMappingId(),mappingVersion,command.mappingSchemaHash(),
                IssueProjectionOperation.CREATE,document,eventId,sequence,Math.max(0,command.expectedProjectionVersion()),
                "projection-intent:"+projectionId+":"+sequence,created);
        var validation=validator.validate(intent);
        return new ProjectionIntentPreview(key,projectionId,document,hash(canonical(document)),validation.violations(),validation.valid());
    }

    private String canonical(ExternalIssueDocument d) {
        return String.join("|",String.valueOf(d.schemaVersion()),d.projectionId(),d.taskId(),d.summary(),d.description(),
                d.issueType(),d.priority(),String.valueOf(d.labels()),String.valueOf(d.components()),
                String.valueOf(d.approvedContext()),String.valueOf(d.comments()),String.valueOf(d.links()),
                String.valueOf(d.sourceEvidence()),String.valueOf(d.mappingVersion()),d.mappingSchemaHash());
    }
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String required(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" is required");return value.trim();}
}
