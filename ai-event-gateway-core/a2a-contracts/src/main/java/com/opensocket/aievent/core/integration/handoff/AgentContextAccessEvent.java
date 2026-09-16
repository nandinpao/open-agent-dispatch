package com.opensocket.aievent.core.integration.handoff;
import java.time.OffsetDateTime;
public record AgentContextAccessEvent(String tenantId,String accessEventId,String taskId,String assignmentId,String dispatchRequestId,String agentId,String agentSessionId,String snapshotId,int snapshotVersion,String accessDecision,String reasonCode,String dispatchTokenHash,String clientAddress,String correlationId,OffsetDateTime accessedAt) {}
