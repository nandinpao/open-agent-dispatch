package com.opensocket.aievent.database.persistence.task.converter;

import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.assignment.DispatchEligibilityStatus;
import com.opensocket.aievent.core.assignment.TaskDispatchAttempt;
import com.opensocket.aievent.core.assignment.TaskDispatchAttemptStatus;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;
import com.opensocket.aievent.database.persistence.task.po.TaskDispatchAttemptPo;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "assignment", name = "store", havingValue = "MYBATIS")
public class TaskDispatchAttemptPersistenceConverter {
    private final ObjectMapper objectMapper;

    public TaskDispatchAttemptPersistenceConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TaskDispatchAttemptPo toPo(TaskDispatchAttempt attempt) {
        TaskDispatchAttemptPo po = new TaskDispatchAttemptPo();
        po.setDispatchAttemptId(attempt.getDispatchAttemptId());
        po.setTaskId(attempt.getTaskId());
        po.setIncidentId(attempt.getIncidentId());
        po.setRoutingDecisionId(attempt.getRoutingDecisionId());
        po.setSelectedAgentId(attempt.getSelectedAgentId());
        po.setSelectedGatewayNodeId(attempt.getSelectedGatewayNodeId());
        po.setSelectedAgentSessionId(attempt.getSelectedAgentSessionId());
        po.setSelectedSiteId(attempt.getSelectedSiteId());
        po.setSelectedScore(attempt.getSelectedScore());
        po.setStatus(attempt.getStatus() == null ? null : attempt.getStatus().name());
        po.setEligibilityStatus(attempt.getEligibilityStatus() == null ? null : attempt.getEligibilityStatus().name());
        po.setDecisionReason(attempt.getDecisionReason());
        po.setScoreBreakdownJson(toJson(attempt.getScoreBreakdown()));
        po.setEligibilityFactsJson(toJson(attempt.getEligibilityFacts()));
        po.setCreatedAt(attempt.getCreatedAt());
        po.setUpdatedAt(attempt.getUpdatedAt());
        return po;
    }

    public TaskDispatchAttempt toDomain(TaskDispatchAttemptPo po) {
        TaskDispatchAttempt attempt = new TaskDispatchAttempt();
        attempt.setDispatchAttemptId(po.getDispatchAttemptId());
        attempt.setTaskId(po.getTaskId());
        attempt.setIncidentId(po.getIncidentId());
        attempt.setRoutingDecisionId(po.getRoutingDecisionId());
        attempt.setSelectedAgentId(po.getSelectedAgentId());
        attempt.setSelectedGatewayNodeId(po.getSelectedGatewayNodeId());
        attempt.setSelectedAgentSessionId(po.getSelectedAgentSessionId());
        attempt.setSelectedSiteId(po.getSelectedSiteId());
        attempt.setSelectedScore(po.getSelectedScore());
        attempt.setStatus(po.getStatus() == null ? null : TaskDispatchAttemptStatus.valueOf(po.getStatus()));
        attempt.setEligibilityStatus(po.getEligibilityStatus() == null ? null : DispatchEligibilityStatus.valueOf(po.getEligibilityStatus()));
        attempt.setDecisionReason(po.getDecisionReason());
        attempt.setScoreBreakdown(fromJson(po.getScoreBreakdownJson()));
        attempt.setEligibilityFacts(fromJson(po.getEligibilityFactsJson()));
        attempt.setCreatedAt(po.getCreatedAt());
        attempt.setUpdatedAt(po.getUpdatedAt());
        return attempt;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
