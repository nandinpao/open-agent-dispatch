package com.opensocket.aievent.core.capability;
import java.time.OffsetDateTime;
public record CaseIssueProjection(String tenantId,String projectionId,String caseId,String connectorType,String targetRef,boolean primaryProjection,String status,String existingProjectionRef,String externalIssueRef,String idempotencyKey,OffsetDateTime createdAt,OffsetDateTime updatedAt){}
