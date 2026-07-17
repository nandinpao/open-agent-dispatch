package com.opensocket.aievent.core.container;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionStatus;
import com.opensocket.aievent.core.action.AdapterActionType;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.action.AdapterActionRepository;
import com.opensocket.aievent.database.persistence.adapter.repository.MybatisAdapterActionRepository;
import com.opensocket.aievent.database.persistence.adapter.converter.AdapterActionPersistenceConverter;
import com.opensocket.aievent.database.persistence.adapter.dao.AdapterActionDao;
import com.opensocket.aievent.core.callback.TaskCallbackRepository;
import com.opensocket.aievent.database.persistence.execution.repository.MybatisTaskCallbackRepository;
import com.opensocket.aievent.database.persistence.execution.converter.TaskCallbackPersistenceConverter;
import com.opensocket.aievent.database.persistence.execution.dao.TaskCallbackDao;
import com.opensocket.aievent.core.callback.TaskCallbackRecord;
import com.opensocket.aievent.core.callback.TaskCallbackType;
import com.opensocket.aievent.core.event.EventSeverity;
import com.opensocket.aievent.core.incident.Incident;
import com.opensocket.aievent.core.incident.IncidentStatus;
import com.opensocket.aievent.core.incident.IncidentRepository;
import com.opensocket.aievent.database.persistence.incident.repository.MybatisIncidentRepository;
import com.opensocket.aievent.database.persistence.incident.converter.IncidentPersistenceConverter;
import com.opensocket.aievent.database.persistence.incident.dao.IncidentDao;
import com.opensocket.aievent.core.task.TaskRepository;
import com.opensocket.aievent.database.persistence.task.repository.MybatisTaskRepository;
import com.opensocket.aievent.database.persistence.task.converter.TaskPersistenceConverter;
import com.opensocket.aievent.database.persistence.task.dao.TaskDao;
import com.opensocket.aievent.core.task.TaskPriority;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;

@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class PostgresRepositoryIdempotencyContainerTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("aeg_core")
            .withUsername("aeg")
            .withPassword("aeg");

    private JdbcTemplate jdbcTemplate;
    private IncidentRepository incidentRepository;
    private TaskRepository taskRepository;
    private TaskCallbackRepository callbackRepository;
    private AdapterActionRepository actionRepository;

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

        ObjectMapper objectMapper = JsonMapper.builder().build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        MybatisContainerTestSupport mybatis = new MybatisContainerTestSupport(dataSource);
        incidentRepository = new MybatisIncidentRepository(mybatis.mapper(IncidentDao.class), new IncidentPersistenceConverter());
        taskRepository = new MybatisTaskRepository(mybatis.mapper(TaskDao.class), new TaskPersistenceConverter(objectMapper));
        callbackRepository = new MybatisTaskCallbackRepository(mybatis.mapper(TaskCallbackDao.class), new TaskCallbackPersistenceConverter(objectMapper));
        actionRepository = new MybatisAdapterActionRepository(mybatis.mapper(AdapterActionDao.class), new AdapterActionPersistenceConverter(objectMapper));
    }

    @Test
    void concurrentActiveIncidentInsertShouldReturnOneActiveIncident() throws Exception {
        int calls = 32;
        List<Incident> results = runConcurrent(calls, index ->
                incidentRepository.saveNewOrGetActive(incident("inc-" + index, "fp-shared")));

        assertThat(results.stream().map(Incident::getIncidentId).distinct()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from incidents", Long.class)).isEqualTo(1L);
    }

    @Test
    void concurrentOpenTaskInsertShouldReturnOneOpenTask() throws Exception {
        incidentRepository.saveNewOrGetActive(incident("inc-1", "fp-task"));

        int calls = 32;
        List<TaskRecord> results = runConcurrent(calls, index ->
                taskRepository.saveNewOrGetOpen(task("task-" + index, "inc-1", TaskType.INCIDENT_RESPONSE)));

        assertThat(results.stream().map(TaskRecord::getTaskId).distinct()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from tasks", Long.class)).isEqualTo(1L);
    }

    @Test
    void concurrentCallbackReserveShouldAcceptOnlyOneCallbackId() throws Exception {
        int calls = 32;
        List<Boolean> results = runConcurrent(calls, index ->
                callbackRepository.tryReserve(callback("cb-shared")));

        assertThat(results).containsOnly(true, false);
        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from task_callbacks", Long.class)).isEqualTo(1L);
    }

    @Test
    void concurrentAdapterActionInsertShouldReturnOneIdempotencyKey() throws Exception {
        int calls = 32;
        List<AdapterAction> results = runConcurrent(calls, index ->
                actionRepository.saveNewOrGetByIdempotencyKey(action("act-" + index, "idem-shared")));

        assertThat(results.stream().map(AdapterAction::getActionId).distinct()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from adapter_actions", Long.class)).isEqualTo(1L);
    }

    private DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private <T> List<T> runConcurrent(int calls, ThrowingIndexedSupplier<T> supplier) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < calls; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return supplier.get(index);
            }));
        }
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(15, TimeUnit.SECONDS));
        }
        executor.shutdownNow();
        return results;
    }

    private Incident incident(String id, String fingerprint) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Incident incident = new Incident();
        incident.setIncidentId(id);
        incident.setFingerprint(fingerprint);
        incident.setTenantId("tenant-a");
        incident.setSourceSystem("MES");
        incident.setSiteId("TNN");
        incident.setPlantId("TNN-FAB-01");
        incident.setObjectType("EQUIPMENT");
        incident.setObjectId("EQP-1001");
        incident.setEventType("EQUIPMENT_ALARM");
        incident.setErrorCode("TEMP_HIGH");
        incident.setSeverity(EventSeverity.HIGH);
        incident.setStatus(IncidentStatus.ACTIVE);
        incident.setFirstSeenAt(now);
        incident.setLastSeenAt(now);
        incident.setOccurrenceCount(1);
        incident.setLastMessage("chamber temperature over threshold");
        return incident;
    }

    private TaskRecord task(String id, String incidentId, TaskType type) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskRecord task = new TaskRecord();
        task.setTaskId(id);
        task.setIncidentId(incidentId);
        task.setSourceEventId("evt-1");
        task.setTaskType(type);
        task.setStatus(TaskStatus.QUEUED);
        task.setPriority(TaskPriority.HIGH);
        task.setTenantId("tenant-a");
        task.setSiteId("TNN");
        task.setPlantId("TNN-FAB-01");
        task.setObjectType("EQUIPMENT");
        task.setObjectId("EQP-1001");
        task.setEventType("EQUIPMENT_ALARM");
        task.setErrorCode("TEMP_HIGH");
        task.setRoutingPolicy("MANUAL_REVIEW");
        task.setRequiredCapabilities(List.of("incident-analysis"));
        task.setCreatedReason("test");
        task.setOccurrenceCountAtCreation(30);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    private TaskCallbackRecord callback(String id) {
        TaskCallbackRecord record = new TaskCallbackRecord();
        record.setCallbackId(id);
        record.setCallbackType(TaskCallbackType.RESULT);
        record.setTaskId("task-1");
        record.setMessage("done");
        record.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return record;
    }

    private AdapterAction action(String id, String idempotencyKey) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AdapterAction action = new AdapterAction();
        action.setActionId(id);
        action.setIdempotencyKey(idempotencyKey);
        action.setIncidentId("inc-1");
        action.setTaskId("task-1");
        action.setAdapterName("mock");
        action.setAdapterType(AdapterType.MCP);
        action.setActionType(AdapterActionType.MCP_CONTEXT_FETCH);
        action.setStatus(AdapterActionStatus.PENDING);
        action.setCreatedAt(now);
        action.setUpdatedAt(now);
        action.setMaxAttempts(3);
        return action;
    }

    @FunctionalInterface
    private interface ThrowingIndexedSupplier<T> {
        T get(int index) throws Exception;
    }
}
