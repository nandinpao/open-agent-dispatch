package com.opensocket.aievent.core.container;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import com.opensocket.aievent.core.agent.governance.AgentGovernanceRepository;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowStore;
import com.opensocket.aievent.core.agent.skill.AgentSkillRegistryRepository;
import com.opensocket.aievent.core.callback.TaskCallbackRepository;
import com.opensocket.aievent.core.dispatch.DispatchEligibilityStatus;
import com.opensocket.aievent.core.dispatch.DispatchMethod;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestRepository;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.dispatch.DispatchReviewMode;
import com.opensocket.aievent.database.persistence.agent.dao.AgentGovernanceDao;
import com.opensocket.aievent.database.persistence.agent.governance.converter.AgentGovernancePersistenceConverter;
import com.opensocket.aievent.database.persistence.agent.governance.repository.MybatisAgentGovernanceRepository;
import com.opensocket.aievent.database.persistence.agent.remediation.dao.AgentRemediationWorkflowDao;
import com.opensocket.aievent.database.persistence.agent.remediation.repository.MybatisAgentRemediationWorkflowStore;
import com.opensocket.aievent.database.persistence.agent.skill.converter.AgentSkillRegistryPersistenceConverter;
import com.opensocket.aievent.database.persistence.agent.skill.dao.AgentSkillRegistryDao;
import com.opensocket.aievent.database.persistence.agent.skill.repository.MybatisAgentSkillRegistryRepository;
import com.opensocket.aievent.database.persistence.execution.converter.DispatchRequestPersistenceConverter;
import com.opensocket.aievent.database.persistence.execution.converter.TaskCallbackPersistenceConverter;
import com.opensocket.aievent.database.persistence.execution.dao.DispatchRequestDao;
import com.opensocket.aievent.database.persistence.execution.dao.TaskCallbackDao;
import com.opensocket.aievent.database.persistence.execution.repository.MybatisDispatchRequestRepository;
import com.opensocket.aievent.database.persistence.execution.repository.MybatisTaskCallbackRepository;

@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
abstract class P25RepositoryDbContainerSupport {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("aeg_core")
            .withUsername("aeg")
            .withPassword("aeg");

    protected JdbcTemplate jdbc;
    protected ObjectMapper objectMapper;
    protected MybatisContainerTestSupport mybatis;

    @BeforeEach
    void resetDatabase() {
        DataSource dataSource = dataSource();
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();

        jdbc = new JdbcTemplate(dataSource);
        objectMapper = JsonMapper.builder().build();
        mybatis = new MybatisContainerTestSupport(dataSource);
    }

    protected AgentGovernanceRepository agentGovernanceRepository() {
        return new MybatisAgentGovernanceRepository(
                mybatis.mapper(AgentGovernanceDao.class),
                new AgentGovernancePersistenceConverter(objectMapper));
    }

    protected DispatchRequestRepository dispatchRequestRepository() {
        return new MybatisDispatchRequestRepository(
                mybatis.mapper(DispatchRequestDao.class),
                new DispatchRequestPersistenceConverter(objectMapper));
    }

    protected TaskCallbackRepository taskCallbackRepository() {
        return new MybatisTaskCallbackRepository(
                mybatis.mapper(TaskCallbackDao.class),
                new TaskCallbackPersistenceConverter(objectMapper));
    }

    protected AgentRemediationWorkflowStore remediationWorkflowStore() {
        return new MybatisAgentRemediationWorkflowStore(mybatis.mapper(AgentRemediationWorkflowDao.class));
    }

    protected AgentSkillRegistryRepository skillRegistryRepository() {
        return new MybatisAgentSkillRegistryRepository(
                mybatis.mapper(AgentSkillRegistryDao.class),
                new AgentSkillRegistryPersistenceConverter(objectMapper));
    }

    protected void seedDispatchParents(String incidentId, String taskId, String assignmentId) {
        OffsetDateTime now = now();
        jdbc.update("""
                insert into incidents(
                    incident_id,fingerprint,tenant_id,source_system,severity,status,
                    first_seen_at,last_seen_at,occurrence_count,created_at,updated_at)
                values (?,?,?,?,?,?,?,?,?,?,?)
                """,
                incidentId, "fp-" + incidentId, "tenant-a", "MES", "HIGH", "ACTIVE",
                now, now, 1, now, now);
        jdbc.update("""
                insert into tasks(
                    task_id,incident_id,task_type,status,priority,tenant_id,routing_policy,
                    required_capabilities_json,occurrence_count_at_creation,created_at,updated_at)
                values (?,?,?,?,?,?,?,cast(? as jsonb),?,?,?)
                """,
                taskId, incidentId, "INCIDENT_RESPONSE", "ASSIGNED", "HIGH", "tenant-a",
                "AUTO", "[]", 1, now, now);
        jdbc.update("""
                insert into task_assignments(
                    assignment_id,task_id,incident_id,status,routing_policy,score,created_at,updated_at)
                values (?,?,?,?,?,?,?,?)
                """,
                assignmentId, taskId, incidentId, "ASSIGNED", "AUTO", 100, now, now);
    }

    protected DispatchRequest dispatch(String id, String incidentId, String taskId, String assignmentId) {
        OffsetDateTime createdAt = now().minusMinutes(1);
        DispatchRequest request = new DispatchRequest();
        request.setDispatchRequestId(id);
        request.setAssignmentId(assignmentId);
        request.setTaskId(taskId);
        request.setIncidentId(incidentId);
        request.setAgentId("agent-db-1");
        request.setOwnerGatewayNodeId("gateway-1");
        request.setAgentSessionId("session-1");
        request.setSiteId("TNN");
        request.setStatus(DispatchRequestStatus.APPROVED);
        request.setReviewMode(DispatchReviewMode.AUTO_APPROVE);
        request.setEligibilityStatus(DispatchEligibilityStatus.ELIGIBLE);
        request.setDispatchMethod(DispatchMethod.INTERNAL_GATEWAY_HTTP);
        request.setGatewayDispatchPath("/internal/gateway/tasks/dispatch");
        request.setDispatchToken("dispatch-token-1");
        request.setReason("P25 repository DB hardening");
        request.setCreatedAt(createdAt);
        request.setUpdatedAt(createdAt);
        request.setApprovedAt(createdAt);
        return request;
    }

    protected OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    protected <T> List<T> runConcurrent(List<ThrowingSupplier<T>> suppliers) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(suppliers.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (ThrowingSupplier<T> supplier : suppliers) {
            futures.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return supplier.get();
            }));
        }
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(20, TimeUnit.SECONDS));
        }
        executor.shutdownNow();
        return results;
    }

    protected com.opensocket.aievent.core.kernel.persistence.ClaimRequest claim(String workerId, OffsetDateTime now) {
        return com.opensocket.aievent.core.kernel.persistence.ClaimRequest.forLease(
                workerId,
                now,
                Duration.ofSeconds(30),
                10);
    }

    private DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    @FunctionalInterface
    protected interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
