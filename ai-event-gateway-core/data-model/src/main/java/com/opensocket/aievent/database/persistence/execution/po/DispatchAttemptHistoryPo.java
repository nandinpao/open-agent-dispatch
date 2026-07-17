package com.opensocket.aievent.database.persistence.execution.po;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DispatchAttemptHistoryPo {
    private String historyId;
    private String taskId;
    private String incidentId;
    private String assignmentId;
    private String dispatchRequestId;
    private String agentId;
    private String ownerGatewayNodeId;
    private String agentSessionId;
    private String siteId;
    private String routingDecisionId;
    private String eventType;
    private String status;
    private Integer attemptNo;
    private Integer taskDispatchAttemptNo;
    private String reason;
    private String errorCode;
    private String errorMessage;
    private OffsetDateTime nextAttemptAt;
    private OffsetDateTime runtimeBackoffUntil;
    private String workerId;
    private OffsetDateTime claimUntil;
    private String payloadJson;
    private OffsetDateTime occurredAt;
    private OffsetDateTime createdAt;
}
