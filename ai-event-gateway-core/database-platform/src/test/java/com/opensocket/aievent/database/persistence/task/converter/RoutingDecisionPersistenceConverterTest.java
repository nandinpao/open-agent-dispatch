package com.opensocket.aievent.database.persistence.task.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.routing.RoutingPolicy;
import com.opensocket.aievent.database.persistence.task.po.RoutingDecisionPo;

class RoutingDecisionPersistenceConverterTest {

    private final RoutingDecisionPersistenceConverter converter =
            new RoutingDecisionPersistenceConverter(JsonMapper.builder().build());

    @Test
    void preservesTenantAcrossPersistenceBoundary() {
        RoutingDecisionRecord source = new RoutingDecisionRecord();
        source.setDecisionId("route-1");
        source.setTaskId("task-1");
        source.setTenantId("tenant-a");
        source.setRoutingPolicy(RoutingPolicy.FLOW_RULE);

        RoutingDecisionPo po = converter.toPo(source);
        RoutingDecisionRecord restored = converter.toDomain(po);

        assertEquals("tenant-a", po.getTenantId());
        assertEquals("tenant-a", restored.getTenantId());
    }

    @Test
    void rejectsTenantlessDecisionBeforeDatabaseCall() {
        RoutingDecisionRecord source = new RoutingDecisionRecord();
        source.setDecisionId("route-tenantless");
        source.setTaskId("task-tenantless");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> converter.toPo(source));

        assertEquals("ROUTING_DECISION_TENANT_REQUIRED", error.getMessage());
    }

    @Test
    void convertsRetiredHistoricalPolicyWithoutEnumFailure() {
        RoutingDecisionPo po = new RoutingDecisionPo();
        po.setRoutingPolicy("ERP_CAPABILITY_FIRST");
        po.setCandidatesJson("[]");

        RoutingDecisionRecord decision = converter.toDomain(po);

        assertEquals(RoutingPolicy.MANUAL_REVIEW, decision.getRoutingPolicy());
    }

    @Test
    void preservesCurrentGenericPolicy() {
        RoutingDecisionPo po = new RoutingDecisionPo();
        po.setRoutingPolicy("FLOW_RULE");
        po.setCandidatesJson("[]");

        RoutingDecisionRecord decision = converter.toDomain(po);

        assertEquals(RoutingPolicy.FLOW_RULE, decision.getRoutingPolicy());
    }
}
