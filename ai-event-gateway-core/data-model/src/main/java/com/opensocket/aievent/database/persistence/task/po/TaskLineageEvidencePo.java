package com.opensocket.aievent.database.persistence.task.po;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class TaskLineageEvidencePo {
    private String tenantId; private String evidenceId; private String rootTaskId; private String taskId; private String parentTaskId;
    private String eventType; private String originPrincipalType; private String originPrincipalId; private String actorPrincipalType; private String actorPrincipalId;
    private String executorAgentId; private String assignmentId; private String departmentId; private String groupId; private String credentialId; private String oauthClientId;
    private String sourceSystem; private String correlationId; private String traceId; private String failureDomain; private String failureCode; private String reason;
    private OffsetDateTime occurredAt;
}
