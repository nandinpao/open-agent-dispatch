package com.opensocket.aievent.core.routing.governance.routing;

import java.util.List;

public interface GenericCandidateAgentRepository {
    List<String> findExplicitFlowAgentIds(String tenantId, String flowId, String eventStage, int limit);
}
