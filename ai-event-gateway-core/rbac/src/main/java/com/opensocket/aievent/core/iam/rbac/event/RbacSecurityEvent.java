package com.opensocket.aievent.core.iam.rbac.event;

import java.time.Instant;
import java.util.Map;
public record RbacSecurityEvent(String eventId,String eventType,String tenantId,String actorId,String principalId,String roleId,String permission,String reasonCode,Map<String,String> metadata,Instant occurredAt){public RbacSecurityEvent{if(eventId==null||eventId.isBlank())throw new IllegalArgumentException("eventId is required");if(eventType==null||eventType.isBlank())throw new IllegalArgumentException("eventType is required");tenantId=tenantId==null?"":tenantId.trim();actorId=actorId==null?"":actorId.trim();principalId=principalId==null?"":principalId.trim();roleId=roleId==null?"":roleId.trim();permission=permission==null?"":permission.trim();reasonCode=reasonCode==null?"":reasonCode.trim();metadata=metadata==null?Map.of():Map.copyOf(metadata);if(occurredAt==null)throw new IllegalArgumentException("occurredAt is required");}}
