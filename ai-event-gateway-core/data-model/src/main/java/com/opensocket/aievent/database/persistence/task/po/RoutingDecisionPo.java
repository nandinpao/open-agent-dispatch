package com.opensocket.aievent.database.persistence.task.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class RoutingDecisionPo {
    private String decisionId; private String taskId; private String incidentId; private String routingPolicy; private String status; private String selectedAgentId;
    private String selectedGatewayNodeId; private String selectedAgentSessionId; private String selectedSiteId; private int selectedScore; private String decisionReason;
    private String userFacingErrorJson; private String candidatesJson; private OffsetDateTime createdAt;
}
