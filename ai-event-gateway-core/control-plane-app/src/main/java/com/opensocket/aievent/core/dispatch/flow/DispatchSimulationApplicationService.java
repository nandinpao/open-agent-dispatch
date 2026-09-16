package com.opensocket.aievent.core.dispatch.flow;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.routing.RoutingSimulationService;

/**
 * Transactional application boundary for no-side-effect Dispatch Simulation.
 *
 * <p>The production routing repository uses direct JdbcTemplate access. Unlike MyBatis IAM
 * repositories, that path is not covered by TenantTransactionMybatisInterceptor. Bind the
 * authenticated Tenant/actor to PostgreSQL transaction-local RLS context before evaluating
 * Flow Rules so {@code flow_rule_attribute_criteria} and future tenant-guarded routing tables
 * are read under the same tenant boundary as the HTTP request.</p>
 */
@Service
public class DispatchSimulationApplicationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final RoutingSimulationService routingSimulationService;

    public DispatchSimulationApplicationService(NamedParameterJdbcTemplate jdbc,
                                                RoutingSimulationService routingSimulationService) {
        this.jdbc = jdbc;
        this.routingSimulationService = routingSimulationService;
    }

    @Transactional
    public DispatchSimulationResponse simulate(DispatchSimulationRequest request) {
        DispatchSimulationRequest simulationRequest = request == null ? new DispatchSimulationRequest() : request;
        DispatchFlowPersistenceTenantContext.bind(jdbc, simulationRequest.getTenantId(), "dispatch-simulation");
        return routingSimulationService.simulate(simulationRequest);
    }
}
