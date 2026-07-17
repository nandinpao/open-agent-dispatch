package com.opensocket.aievent.core.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.opensocket.aievent.core.decision.DecisionAction;
import com.opensocket.aievent.core.decision.DecisionEngine;
import com.opensocket.aievent.core.decision.EventIntakeDecisionResponse;
import com.opensocket.aievent.database.persistence.eventprocessing.repository.MybatisEventDecisionRepository;
import com.opensocket.aievent.database.persistence.eventprocessing.converter.EventDecisionPersistenceConverter;
import com.opensocket.aievent.database.persistence.eventprocessing.dao.EventDecisionDao;
import com.opensocket.aievent.core.dedup.EventDedupRedisProperties;
import com.opensocket.aievent.core.dedup.RedisDedupStateStore;
import com.opensocket.aievent.database.persistence.eventprocessing.repository.MybatisDedupStateSnapshotRepository;
import com.opensocket.aievent.database.persistence.eventprocessing.converter.DedupStateSnapshotPersistenceConverter;
import com.opensocket.aievent.database.persistence.eventprocessing.dao.DedupStateSnapshotDao;
import com.opensocket.aievent.core.event.EventIntakeRequest;
import com.opensocket.aievent.core.fingerprint.DynamicTokenMasker;
import com.opensocket.aievent.core.fingerprint.FingerprintFieldResolver;
import com.opensocket.aievent.core.fingerprint.FingerprintGenerator;
import com.opensocket.aievent.core.fingerprint.FingerprintPolicyProperties;
import com.opensocket.aievent.core.fingerprint.FingerprintPolicyResolver;
import com.opensocket.aievent.core.incident.IncidentManager;
import com.opensocket.aievent.core.incident.DefaultIncidentFacade;
import com.opensocket.aievent.core.incident.IncidentRepository;
import com.opensocket.aievent.database.persistence.incident.repository.MybatisIncidentRepository;
import com.opensocket.aievent.database.persistence.incident.converter.IncidentPersistenceConverter;
import com.opensocket.aievent.database.persistence.incident.dao.IncidentDao;
import com.opensocket.aievent.core.normalize.EventNormalizer;
import com.opensocket.aievent.core.processing.DefaultEventProcessingFacade;
import com.opensocket.aievent.core.processing.EventProcessingProperties;
import com.opensocket.aievent.database.persistence.incident.repository.MybatisIncidentOccurrenceSummaryRepository;
import com.opensocket.aievent.database.persistence.incident.converter.IncidentOccurrenceSummaryPersistenceConverter;
import com.opensocket.aievent.database.persistence.incident.dao.IncidentOccurrenceSummaryDao;
import com.opensocket.aievent.core.task.TaskDecisionResult;
import com.opensocket.aievent.core.task.TaskOrchestrationFacade;

/**
 * M0 integration baseline for the critical event-intake consistency boundary:
 * Redis owns atomic hot dedup state while PostgreSQL persists incident,
 * occurrence summary, dedup snapshot, and decision audit records.
 */
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class CorePostgresRedisBaselineContainerTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("aeg_core")
            .withUsername("aeg")
            .withPassword("aeg");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory redisConnectionFactory;
    private StringRedisTemplate redisTemplate;
    private JdbcTemplate jdbcTemplate;
    private DecisionEngine decisionEngine;
    private RedisDedupStateStore dedupStateStore;

    @BeforeEach
    void setUp() {
        DataSource dataSource = dataSource();
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);

        redisConnectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        redisConnectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(redisConnectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        EventDedupRedisProperties redisProperties = new EventDedupRedisProperties();
        redisProperties.setKeyPrefix("aeg-m0-" + UUID.randomUUID());
        dedupStateStore = new RedisDedupStateStore(redisTemplate, redisProperties);

        ObjectMapper objectMapper = JsonMapper.builder().build();
        MybatisContainerTestSupport mybatis = new MybatisContainerTestSupport(dataSource);
        IncidentRepository incidentRepository = new MybatisIncidentRepository(mybatis.mapper(IncidentDao.class), new IncidentPersistenceConverter());
        FingerprintPolicyProperties fingerprintProperties = new FingerprintPolicyProperties();
        DynamicTokenMasker tokenMasker = new DynamicTokenMasker(fingerprintProperties);
        FingerprintGenerator fingerprintGenerator = new FingerprintGenerator(
                fingerprintProperties,
                new FingerprintPolicyResolver(fingerprintProperties),
                new FingerprintFieldResolver(tokenMasker));

        TaskOrchestrationFacade taskOrchestrationFacade = mock(TaskOrchestrationFacade.class);
        when(taskOrchestrationFacade.decide(any(), any(), any())).thenReturn(
                TaskDecisionResult.suppressed(
                        "M0 integration baseline keeps task creation outside this consistency test",
                        List.of(DecisionAction.TASK_RECOMMENDED_BUT_DISABLED)));

        EventProcessingProperties eventProperties = new EventProcessingProperties();
        decisionEngine = new DecisionEngine(
                new DefaultEventProcessingFacade(
                        new EventNormalizer(),
                        fingerprintGenerator,
                        dedupStateStore,
                        new MybatisDedupStateSnapshotRepository(mybatis.mapper(DedupStateSnapshotDao.class), new DedupStateSnapshotPersistenceConverter()),
                        new DefaultIncidentFacade(
                                new IncidentManager(incidentRepository),
                                incidentRepository,
                                new MybatisIncidentOccurrenceSummaryRepository(
                                        mybatis.mapper(IncidentOccurrenceSummaryDao.class),
                                        new IncidentOccurrenceSummaryPersistenceConverter(objectMapper))),
                        eventProperties),
                new MybatisEventDecisionRepository(
                        mybatis.mapper(EventDecisionDao.class),
                        new EventDecisionPersistenceConverter(objectMapper)),
                taskOrchestrationFacade);
    }

    @AfterEach
    void tearDown() {
        if (redisConnectionFactory != null) {
            redisConnectionFactory.destroy();
        }
    }

    @Test
    void duplicateEventShouldRemainConsistentAcrossRedisAndPostgres() {
        EventIntakeDecisionResponse first = decisionEngine.ingest(request(
                "ERP order SO202606110001 failed at 2026-06-11 08:10:01 from 10.1.2.3",
                OffsetDateTime.of(2026, 6, 11, 8, 10, 1, 0, ZoneOffset.UTC)));
        EventIntakeDecisionResponse second = decisionEngine.ingest(request(
                "ERP order SO202606110999 failed at 2026-06-11 08:11:15 from 10.9.8.7",
                OffsetDateTime.of(2026, 6, 11, 8, 11, 15, 0, ZoneOffset.UTC)));

        assertThat(second.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(second.incidentId()).isEqualTo(first.incidentId());
        assertThat(second.duplicate()).isTrue();
        assertThat(second.occurrenceCount()).isEqualTo(2L);

        assertThat(dedupStateStore.find(first.fingerprint())).isPresent()
                .get()
                .satisfies(state -> {
                    assertThat(state.getOccurrenceCount()).isEqualTo(2L);
                    assertThat(state.getActiveIncidentId()).isEqualTo(first.incidentId());
                });

        assertThat(jdbcTemplate.queryForObject("select count(*) from incidents", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select occurrence_count from incidents where incident_id = ?", Long.class,
                first.incidentId())).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from event_decisions", Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("select occurrence_count from event_dedup_state where fingerprint = ?", Long.class,
                first.fingerprint())).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("select sum(occurrence_count) from incident_occurrence_summary where incident_id = ?", Long.class,
                first.incidentId())).isEqualTo(2L);
    }

    private DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private EventIntakeRequest request(String message, OffsetDateTime occurredAt) {
        EventIntakeRequest request = new EventIntakeRequest();
        request.setTenantId("tenant-a");
        request.setSourceSystem("ERP");
        request.setSiteId("TNN");
        request.setPlantId("TNN-FAB-01");
        request.setObjectType("ORDER");
        request.setObjectId("ORDER-IMPORT");
        request.setEventType("ORDER_IMPORT_FAILED");
        request.setErrorCode("ERP_IMPORT_500");
        request.setSeverity("HIGH");
        request.setMessage(message);
        request.setOccurredAt(occurredAt);
        request.setAttributes(Map.of("module", "SD"));
        return request;
    }
}
