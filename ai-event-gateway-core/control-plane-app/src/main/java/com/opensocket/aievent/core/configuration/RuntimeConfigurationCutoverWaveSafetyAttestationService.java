package com.opensocket.aievent.core.configuration;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.opensocket.aievent.core.capability.runtime.A2ADelegationRuntimeConfigurationView;
import com.opensocket.aievent.core.integration.issue.projection.IssueProjectionRuntimeConfigurationView;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWaveSafetyAttestation;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverWaveSafetyAttestationStore;
import com.opensocket.aievent.core.lifecycle.TaskLifecycleRuntimeConfigurationView;
import com.opensocket.aievent.core.operational.OperationalRuntimeConfigurationView;

import tools.jackson.databind.ObjectMapper;

/** Durable, expiring pre-cutover safety evidence for high-risk cutover waves. */
@Service
public class RuntimeConfigurationCutoverWaveSafetyAttestationService {
    public static final String W4 = "C3R3-W4";
    public static final String W5 = "C3R3-W5";
    public static final String W6 = "C3R3-W6";
    public static final String PROFILE_TASK_DISPATCH = "TASK_DISPATCH";
    public static final String PROFILE_EXTERNAL_INTEGRATION_A2A = "EXTERNAL_INTEGRATION_A2A";
    public static final String PROFILE_PLATFORM_READINESS_RECOVERY = "PLATFORM_READINESS_RECOVERY";
    private static final Duration W4_VALID_FOR = Duration.ofMinutes(15);
    private static final Duration W5_VALID_FOR = Duration.ofMinutes(10);
    private static final Duration W6_VALID_FOR = Duration.ofMinutes(5);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final OperationalRuntimeConfigurationView operational;
    private final TaskLifecycleRuntimeConfigurationView lifecycle;
    private final IssueProjectionRuntimeConfigurationView issueProjection;
    private final A2ADelegationRuntimeConfigurationView a2a;
    private final RuntimeConfigurationCutoverWaveSafetyAttestationStore store;
    private final RuntimeConfigurationCutoverWaveService waves;
    private final ObjectMapper json;
    private final RuntimeConfigurationCutoverWaveAuditService audit;

    public RuntimeConfigurationCutoverWaveSafetyAttestationService(JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager, OperationalRuntimeConfigurationView operational,
            TaskLifecycleRuntimeConfigurationView lifecycle, IssueProjectionRuntimeConfigurationView issueProjection,
            A2ADelegationRuntimeConfigurationView a2a, RuntimeConfigurationCutoverWaveSafetyAttestationStore store,
            RuntimeConfigurationCutoverWaveService waves, ObjectMapper json, RuntimeConfigurationCutoverWaveAuditService audit) {
        this.jdbc = jdbc; this.transactions = new TransactionTemplate(transactionManager); this.operational = operational;
        this.lifecycle = lifecycle; this.issueProjection = issueProjection; this.a2a = a2a;
        this.store = store; this.waves = waves; this.json = json; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AttestationStatus status(String waveId) {
        String id = required(waveId, "waveId");
        boolean required = requiresSafety(id);
        RuntimeConfigurationCutoverWaveSafetyAttestation latest = store.latest(id).orElse(null);
        List<String> blockers = blockers(required, latest, OffsetDateTime.now(ZoneOffset.UTC));
        return new AttestationStatus(id, required, profile(id), latest, blockers);
    }

    @Transactional
    public AttestationStatus assess(String waveId, String actor, String reason, String correlationId) {
        String id = required(waveId, "waveId");
        if (!requiresSafety(id)) throw new IllegalStateException("CONFIGURATION_CUTOVER_WAVE_SAFETY_ATTESTATION_NOT_REQUIRED waveId=" + id);
        var before = waves.status(id);
        String phase = before.phase();
        if ("FINALIZED".equals(phase) || "FINALIZED_WITH_DRIFT".equals(phase))
            throw new IllegalStateException("CONFIGURATION_CUTOVER_WAVE_SAFETY_ATTESTATION_IMMUTABLE_AFTER_FINALIZE waveId=" + id);
        String operator = required(actor, "actor"), why = required(reason, "reason");
        AttestationStatus result = W4.equals(id) ? assessW4(id, operator, why, correlationId)
                : (W5.equals(id) ? assessW5(id, operator, why, correlationId) : assessW6(id, operator, why, correlationId));
        var latest = result.latestAttestation();
        var after = waves.status(id);
        Map<String,Object> auditEvidence = new LinkedHashMap<>();
        auditEvidence.put("profile", result.profile());
        auditEvidence.put("attestationId", latest == null ? null : latest.attestationId());
        auditEvidence.put("attestationStatus", latest == null ? null : latest.status());
        auditEvidence.put("expiresAt", latest == null ? null : latest.expiresAt().toString());
        audit.record("ASSESS_SAFETY", "configuration.cutover.assess", before, after, operator, why, correlationId, auditEvidence);
        return result;
    }

    private AttestationStatus assessW4(String id, String operator, String why, String correlationId) {
        OffsetDateTime captured = OffsetDateTime.now(ZoneOffset.UTC);
        List<String> tenants = activeTenants();
        List<String> failures = new ArrayList<>();
        long dispatchCount = 0L, dispatchOldestMillis = 0L, reconciliationCount = 0L, reconciliationOldestMillis = 0L;
        long staleRunningTasks = 0L, manualRecoveryTasks = 0L, expiredFinalizationClaims = 0L, overdueTaskConditions = 0L, authorityMismatches = 0L;
        long dispatchSloSeconds = operational.dispatchSloSeconds();
        long reconciliationSloSeconds = operational.reconciliationSloSeconds();
        OffsetDateTime staleRunningCutoff = captured.minus(lifecycle.runningTimeout());

        if (tenants.isEmpty()) failures.add("ACTIVE_TENANT_MISSING");
        for (String tenant : tenants) {
            TenantTaskSafety snapshot = transactions.execute(status -> readTaskTenant(tenant, staleRunningCutoff));
            if (snapshot == null) { failures.add("TENANT_SAFETY_QUERY_EMPTY:" + tenant); continue; }
            dispatchCount += snapshot.dispatch().count(); dispatchOldestMillis = Math.max(dispatchOldestMillis, snapshot.dispatch().oldestMillis());
            reconciliationCount += snapshot.reconciliation().count(); reconciliationOldestMillis = Math.max(reconciliationOldestMillis, snapshot.reconciliation().oldestMillis());
            staleRunningTasks += snapshot.staleRunningTasks(); manualRecoveryTasks += snapshot.manualRecoveryTasks();
            expiredFinalizationClaims += snapshot.expiredFinalizationClaims(); overdueTaskConditions += snapshot.overdueTaskConditions();
            authorityMismatches += snapshot.authorityMismatches();
        }
        if (dispatchOldestMillis > dispatchSloSeconds * 1000L) failures.add("DISPATCH_BACKLOG_SLO_BREACH");
        if (reconciliationOldestMillis > reconciliationSloSeconds * 1000L) failures.add("RECONCILIATION_BACKLOG_SLO_BREACH");
        if (staleRunningTasks > 0) failures.add("STALE_RUNNING_TASKS:" + staleRunningTasks);
        if (manualRecoveryTasks > 0) failures.add("TASK_FINALIZATION_MANUAL_RECOVERY:" + manualRecoveryTasks);
        if (expiredFinalizationClaims > 0) failures.add("EXPIRED_FINALIZATION_CLAIMS:" + expiredFinalizationClaims);
        if (overdueTaskConditions > 0) failures.add("OVERDUE_TASK_CONDITIONS:" + overdueTaskConditions);
        if (authorityMismatches > 0) failures.add("TASK_AUTHORITY_WAITING_WITHOUT_BLOCKER:" + authorityMismatches);

        Map<String,Object> evidence = new LinkedHashMap<>();
        evidence.put("stage", "V41_C3R3E_WAVE4_TASK_DISPATCH_TASK_AUTHORITY_SINGLE_AUTHORITY_CUTOVER");
        evidence.put("profile", PROFILE_TASK_DISPATCH); evidence.put("waveId", id); evidence.put("correlationId", text(correlationId)); evidence.put("activeTenantCount", tenants.size());
        evidence.put("dispatch", Map.of("dueCount", dispatchCount, "oldestAgeMillis", dispatchOldestMillis, "sloSeconds", dispatchSloSeconds));
        evidence.put("reconciliation", Map.of("dueCount", reconciliationCount, "oldestAgeMillis", reconciliationOldestMillis, "sloSeconds", reconciliationSloSeconds));
        evidence.put("taskSafety", Map.of("staleRunningTasks", staleRunningTasks, "manualRecoveryTasks", manualRecoveryTasks,
                "expiredFinalizationClaims", expiredFinalizationClaims, "overdueTaskConditions", overdueTaskConditions,
                "authorityMismatches", authorityMismatches, "runningTimeoutMillis", lifecycle.runningTimeout().toMillis()));
        evidence.put("failures", List.copyOf(failures));
        return save(id, operator, why, captured, W4_VALID_FOR, evidence, failures);
    }

    private AttestationStatus assessW5(String id, String operator, String why, String correlationId) {
        OffsetDateTime captured = OffsetDateTime.now(ZoneOffset.UTC);
        List<String> tenants = activeTenants();
        List<String> failures = new ArrayList<>();
        long issuePermanentFailures=0, deadLetters=0, conflicts=0, openCircuits=0, waitHuman=0, expiredA2AClaims=0, unhealthyApprovedInterfaces=0;
        long integrationOutboxCount=0, integrationOutboxOldestMillis=0, a2aReconciliationCount=0, a2aReconciliationOldestMillis=0;
        long integrationStaleLimitMillis = Math.max(300_000L, Math.multiplyExact(Math.multiplyExact(issueProjection.retryDelaySeconds(), (long) issueProjection.maxAttempts()), 1000L));
        long a2aStaleLimitMillis = Math.max(60_000L, Math.multiplyExact(a2a.reconciliationPipelineDelay().toMillis(), 10L));

        if (tenants.isEmpty()) failures.add("ACTIVE_TENANT_MISSING");
        for (String tenant : tenants) {
            TenantExternalSafety snapshot = transactions.execute(status -> readExternalTenant(tenant));
            if (snapshot == null) { failures.add("TENANT_EXTERNAL_SAFETY_QUERY_EMPTY:" + tenant); continue; }
            issuePermanentFailures += snapshot.issuePermanentFailures(); deadLetters += snapshot.deadLetters(); conflicts += snapshot.conflicts();
            openCircuits += snapshot.openCircuits(); waitHuman += snapshot.waitHuman(); expiredA2AClaims += snapshot.expiredA2AClaims();
            unhealthyApprovedInterfaces += snapshot.unhealthyApprovedInterfaces();
            integrationOutboxCount += snapshot.integrationOutbox().count(); integrationOutboxOldestMillis = Math.max(integrationOutboxOldestMillis, snapshot.integrationOutbox().oldestMillis());
            a2aReconciliationCount += snapshot.a2aReconciliation().count(); a2aReconciliationOldestMillis = Math.max(a2aReconciliationOldestMillis, snapshot.a2aReconciliation().oldestMillis());
        }
        if (issuePermanentFailures > 0) failures.add("ISSUE_PERMANENT_SYNC_FAILURES:" + issuePermanentFailures);
        if (deadLetters > 0) failures.add("INTEGRATION_DEAD_LETTERS_OPEN:" + deadLetters);
        if (conflicts > 0) failures.add("INTEGRATION_CONFLICTS_OPEN:" + conflicts);
        if (openCircuits > 0) failures.add("INTEGRATION_CIRCUIT_NOT_CLOSED:" + openCircuits);
        if (integrationOutboxOldestMillis > integrationStaleLimitMillis) failures.add("INTEGRATION_OUTBOX_STALE:" + integrationOutboxOldestMillis);
        if (a2aReconciliationOldestMillis > a2aStaleLimitMillis) failures.add("A2A_RECONCILIATION_STALE:" + a2aReconciliationOldestMillis);
        if (waitHuman > 0) failures.add("A2A_RECONCILIATION_WAIT_HUMAN:" + waitHuman);
        if (expiredA2AClaims > 0) failures.add("A2A_EXPIRED_CLAIMS:" + expiredA2AClaims);
        if (unhealthyApprovedInterfaces > 0) failures.add("A2A_APPROVED_INTERFACE_NOT_RUNTIME_ELIGIBLE:" + unhealthyApprovedInterfaces);

        Map<String,Object> evidence = new LinkedHashMap<>();
        evidence.put("stage", "V41_C3R3F_WAVE5_INCIDENT_ISSUE_INTEGRATION_A2A_SINGLE_AUTHORITY_CUTOVER");
        evidence.put("profile", PROFILE_EXTERNAL_INTEGRATION_A2A); evidence.put("waveId", id); evidence.put("correlationId", text(correlationId)); evidence.put("activeTenantCount", tenants.size());
        evidence.put("integration", Map.of("dueOutboxCount",integrationOutboxCount,"oldestDueAgeMillis",integrationOutboxOldestMillis,
                "staleLimitMillis",integrationStaleLimitMillis,"permanentIssueSyncFailures",issuePermanentFailures,
                "openDeadLetters",deadLetters,"openConflicts",conflicts,"nonClosedCircuits",openCircuits));
        evidence.put("a2a", Map.of("dueReconciliationCount",a2aReconciliationCount,"oldestDueAgeMillis",a2aReconciliationOldestMillis,
                "staleLimitMillis",a2aStaleLimitMillis,"waitHuman",waitHuman,"expiredClaims",expiredA2AClaims,
                "approvedInterfacesNotRuntimeEligible",unhealthyApprovedInterfaces));
        evidence.put("thresholdSources", Map.of("issueRetryDelaySeconds",issueProjection.retryDelaySeconds(),"issueMaxAttempts",issueProjection.maxAttempts(),
                "a2aReconciliationPipelineMillis",a2a.reconciliationPipelineDelay().toMillis()));
        evidence.put("failures", List.copyOf(failures));
        return save(id, operator, why, captured, W5_VALID_FOR, evidence, failures);
    }


    private AttestationStatus assessW6(String id, String operator, String why, String correlationId) {
        OffsetDateTime captured = OffsetDateTime.now(ZoneOffset.UTC);
        List<String> failures = new ArrayList<>();
        bind("INSTANCE", "c3r3g-wave6-platform-readiness");

        long activeEmergencyOverrides = count("select count(*) from runtime_config_emergency_overrides where status='ACTIVE' and expires_at>now()");
        long recentApplyFailures = count("select count(*) from runtime_config_apply_states where state='FAILED' and updated_at>=now()-interval '15 minutes'");
        long platformApplyDrift = count("""
            select count(*) from runtime_config_required_node_targets t
            left join runtime_config_apply_states a on a.config_set_id=t.config_set_id and a.node_id=t.node_id
            where t.required=true and (a.node_id is null or a.state<>'APPLIED' or a.applied_revision_id is distinct from a.desired_revision_id)
            """);
        long activeRevisionDistributionFailures = count("""
            select count(*) from runtime_config_outbox o
            join runtime_config_active_revisions a on a.config_set_id=o.config_set_id and a.revision_id=o.revision_id
            where o.status='FAILED'
            """);
        long staleDistributionWork = count("""
            select count(*) from runtime_config_outbox o
            join runtime_config_active_revisions a on a.config_set_id=o.config_set_id and a.revision_id=o.revision_id
            where o.status in ('PENDING','CLAIMED') and o.updated_at<now()-interval '5 minutes'
            """);
        long w6RecoveryPoints = count("""
            select count(*) from runtime_config_cutover_wave_members m
            join runtime_config_sets s on s.set_key=m.set_key and s.status='ACTIVE'
            join runtime_config_active_revisions a on a.config_set_id=s.config_set_id
            join runtime_config_revisions r on r.config_set_id=a.config_set_id and r.revision_id=a.revision_id and r.state='PUBLISHED'
            where m.wave_id='C3R3-W6'
              and (select count(*) from runtime_config_revision_items i where i.revision_id=r.revision_id)=m.expected_runtime_key_count
            """);
        long w6MissingLastKnownGood = count("""
            select count(*) from runtime_config_cutover_wave_members m
            join runtime_config_sets s on s.set_key=m.set_key and s.status='ACTIVE'
            join runtime_config_required_node_targets t on t.config_set_id=s.config_set_id and t.required=true
            left join runtime_config_apply_states a on a.config_set_id=t.config_set_id and a.node_id=t.node_id
            where m.wave_id='C3R3-W6' and (a.node_id is null or a.state<>'APPLIED' or a.snapshot_fingerprint is null or a.applied_revision_id is distinct from a.desired_revision_id)
            """);
        long onlineGatewayNodes = count("select count(*) from gateway_nodes where status='ONLINE' and lease_expires_at is not null and lease_expires_at>now()");
        long expiredOnlineGatewayNodes = count("select count(*) from gateway_nodes where status='ONLINE' and (lease_expires_at is null or lease_expires_at<=now())");

        List<String> tenants = activeTenants();
        long dispatchCount=0L,dispatchOldestMillis=0L,reconciliationCount=0L,reconciliationOldestMillis=0L;
        long dispatchSloSeconds=operational.dispatchSloSeconds(), reconciliationSloSeconds=operational.reconciliationSloSeconds();
        if(tenants.isEmpty()) failures.add("ACTIVE_TENANT_MISSING");
        for(String tenant:tenants){
            TenantPlatformSafety snapshot=transactions.execute(status->readPlatformTenant(tenant));
            if(snapshot==null){failures.add("TENANT_PLATFORM_SAFETY_QUERY_EMPTY:"+tenant);continue;}
            dispatchCount+=snapshot.dispatch().count(); dispatchOldestMillis=Math.max(dispatchOldestMillis,snapshot.dispatch().oldestMillis());
            reconciliationCount+=snapshot.reconciliation().count(); reconciliationOldestMillis=Math.max(reconciliationOldestMillis,snapshot.reconciliation().oldestMillis());
        }

        if(activeEmergencyOverrides>0) failures.add("ACTIVE_RUNTIME_CONFIG_EMERGENCY_OVERRIDES:"+activeEmergencyOverrides);
        if(recentApplyFailures>0) failures.add("RECENT_RUNTIME_CONFIG_APPLY_FAILURES:"+recentApplyFailures);
        if(platformApplyDrift>0) failures.add("RUNTIME_CONFIG_PLATFORM_DRIFT:"+platformApplyDrift);
        if(activeRevisionDistributionFailures>0) failures.add("ACTIVE_REVISION_DISTRIBUTION_FAILURES:"+activeRevisionDistributionFailures);
        if(staleDistributionWork>0) failures.add("RUNTIME_CONFIG_DISTRIBUTION_STALE:"+staleDistributionWork);
        if(w6RecoveryPoints!=5) failures.add("W6_RECOVERY_POINT_INCOMPLETE:expected=5:actual="+w6RecoveryPoints);
        if(w6MissingLastKnownGood>0) failures.add("W6_LAST_KNOWN_GOOD_SNAPSHOT_MISSING:"+w6MissingLastKnownGood);
        if(onlineGatewayNodes<1) failures.add("ONLINE_GATEWAY_NODE_MISSING");
        if(expiredOnlineGatewayNodes>0) failures.add("ONLINE_GATEWAY_LEASE_EXPIRED:"+expiredOnlineGatewayNodes);
        if(dispatchOldestMillis>dispatchSloSeconds*1000L) failures.add("DISPATCH_BACKLOG_SLO_BREACH");
        if(reconciliationOldestMillis>reconciliationSloSeconds*1000L) failures.add("RECONCILIATION_BACKLOG_SLO_BREACH");

        Map<String,Object> config=new LinkedHashMap<>();
        config.put("activeEmergencyOverrides",activeEmergencyOverrides);
        config.put("recentApplyFailures",recentApplyFailures);
        config.put("platformApplyDrift",platformApplyDrift);
        config.put("activeRevisionDistributionFailures",activeRevisionDistributionFailures);
        config.put("staleDistributionWork",staleDistributionWork);
        config.put("w6CompleteRecoveryPoints",w6RecoveryPoints);
        config.put("w6ExpectedRecoveryPoints",5);
        config.put("w6MissingLastKnownGoodNodeSnapshots",w6MissingLastKnownGood);
        Map<String,Object> gateway=new LinkedHashMap<>();
        gateway.put("onlineLeaseValid",onlineGatewayNodes); gateway.put("onlineLeaseExpired",expiredOnlineGatewayNodes);
        Map<String,Object> dispatch=new LinkedHashMap<>();
        dispatch.put("dueCount",dispatchCount); dispatch.put("oldestAgeMillis",dispatchOldestMillis); dispatch.put("sloSeconds",dispatchSloSeconds);
        Map<String,Object> reconciliation=new LinkedHashMap<>();
        reconciliation.put("dueCount",reconciliationCount); reconciliation.put("oldestAgeMillis",reconciliationOldestMillis); reconciliation.put("sloSeconds",reconciliationSloSeconds);
        Map<String,Object> evidence=new LinkedHashMap<>();
        evidence.put("stage","V41_C3R3G_WAVE6_CORE_EDGE_GATEWAY_SINGLE_AUTHORITY_CUTOVER");
        evidence.put("profile",PROFILE_PLATFORM_READINESS_RECOVERY); evidence.put("waveId",id); evidence.put("correlationId",text(correlationId));
        evidence.put("activeTenantCount",tenants.size()); evidence.put("configurationRecovery",config); evidence.put("gatewayHealth",gateway);
        evidence.put("dispatch",dispatch); evidence.put("reconciliation",reconciliation); evidence.put("failures",List.copyOf(failures));
        return save(id,operator,why,captured,W6_VALID_FOR,evidence,failures);
    }

    private AttestationStatus save(String id,String operator,String why,OffsetDateTime captured,Duration validFor,Map<String,Object> evidence,List<String> failures){
        String status = failures.isEmpty() ? "PASS" : "FAIL";
        var attestation = new RuntimeConfigurationCutoverWaveSafetyAttestation(UUID.randomUUID().toString(), id, status,
                serialize(evidence), operator, why, captured, captured.plus(validFor));
        store.save(attestation);
        return new AttestationStatus(id, true, profile(id), attestation, blockers(true, attestation, OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private List<String> activeTenants(){return jdbc.queryForList("select tenant_id from tenants where status='ACTIVE' order by tenant_id", String.class);}

    private TenantTaskSafety readTaskTenant(String tenant, OffsetDateTime staleRunningCutoff) {
        bind(tenant, "c3r3e-wave4-safety-attestation");
        Backlog dispatch = backlog("""
            select count(*)::bigint, coalesce(extract(epoch from (now()-min(coalesce(updated_at,created_at))))*1000,0)::bigint
              from dispatch_requests where status='APPROVED' or (status='RETRY_WAITING' and (next_retry_at is null or next_retry_at<=now())) or (status='DISPATCHING' and (claim_until is null or claim_until<=now()))
            """);
        Backlog reconciliation = backlog("""
            select count(*)::bigint, coalesce(extract(epoch from (now()-min(coalesce(next_attempt_at,created_at))))*1000,0)::bigint
              from a2a_reconciliation_cases where status in ('OPEN','READY','RETRY_WAITING','CLAIMED','EXECUTING') and coalesce(next_attempt_at,created_at)<=now() and (claim_until is null or claim_until<=now())
            """);
        long staleRunning = count("select count(*) from tasks where task_lifecycle<>'CLOSED' and status='RUNNING' and updated_at<?", staleRunningCutoff);
        long manualRecovery = count("select count(*) from tasks where task_lifecycle='FINALIZING' and finalization_state in ('RETRY_PENDING','MANUAL_RECOVERY_REQUIRED')");
        long expiredClaims = count("select count(*) from tasks where task_lifecycle='FINALIZING' and finalization_state='RUNNING' and finalization_claim_until is not null and finalization_claim_until<=now()");
        long overdueConditions = count("select count(*) from task_conditions where condition_state='ACTIVE' and timeout_at is not null and timeout_at<=now()");
        long authorityMismatch = count("""
            select count(*) from tasks t where t.task_lifecycle='WAITING' and not exists (
              select 1 from task_conditions c where c.tenant_id=t.tenant_id and c.task_id=t.task_id and c.condition_state='ACTIVE' and c.blocking=true)
            """);
        return new TenantTaskSafety(dispatch,reconciliation,staleRunning,manualRecovery,expiredClaims,overdueConditions,authorityMismatch);
    }

    private TenantExternalSafety readExternalTenant(String tenant) {
        bind(tenant, "c3r3f-wave5-safety-attestation");
        Backlog outbox = backlog("""
            select count(*)::bigint, coalesce(extract(epoch from (now()-min(coalesce(next_attempt_at,created_at))))*1000,0)::bigint
              from integration_outbox where status in ('PENDING','FAILED_RETRYABLE') and coalesce(next_attempt_at,created_at)<=now() and (claim_until is null or claim_until<=now())
            """);
        Backlog reconciliation = backlog("""
            select count(*)::bigint, coalesce(extract(epoch from (now()-min(coalesce(next_attempt_at,created_at))))*1000,0)::bigint
              from a2a_reconciliation_cases where status in ('OPEN','READY','RETRY_WAITING','CLAIMED','EXECUTING') and coalesce(next_attempt_at,created_at)<=now() and (claim_until is null or claim_until<=now())
            """);
        long issuePermanentFailures = count("select count(*) from task_issue_links where sync_status in ('FAILED_PERMANENT','CONFLICT')");
        long deadLetters = count("select count(*) from integration_dead_letters where status='OPEN'");
        long conflicts = count("select count(*) from integration_conflicts where status='OPEN'");
        long openCircuits = count("select count(*) from integration_circuit_breakers where state<>'CLOSED'");
        long waitHuman = count("select count(*) from a2a_reconciliation_cases where status='WAIT_HUMAN'");
        long expiredClaims = count("select count(*) from a2a_reconciliation_cases where status in ('CLAIMED','EXECUTING') and claim_until is not null and claim_until<=now()");
        long unhealthyInterfaces = count("""
            select count(*) from a2a_peer_interfaces where status='APPROVED'
             and (health_status<>'HEALTHY' or conformance_status<>'PASS' or circuit_state<>'CLOSED')
            """);
        return new TenantExternalSafety(outbox,reconciliation,issuePermanentFailures,deadLetters,conflicts,openCircuits,waitHuman,expiredClaims,unhealthyInterfaces);
    }


    private TenantPlatformSafety readPlatformTenant(String tenant) {
        bind(tenant,"c3r3g-wave6-platform-readiness");
        Backlog dispatch=backlog("""
            select count(*)::bigint, coalesce(extract(epoch from (now()-min(coalesce(updated_at,created_at))))*1000,0)::bigint
              from dispatch_requests where status='APPROVED' or (status='RETRY_WAITING' and (next_retry_at is null or next_retry_at<=now())) or (status='DISPATCHING' and (claim_until is null or claim_until<=now()))
            """);
        Backlog reconciliation=backlog("""
            select count(*)::bigint, coalesce(extract(epoch from (now()-min(coalesce(next_attempt_at,created_at))))*1000,0)::bigint
              from a2a_reconciliation_cases where status in ('OPEN','READY','RETRY_WAITING','CLAIMED','EXECUTING') and coalesce(next_attempt_at,created_at)<=now() and (claim_until is null or claim_until<=now())
            """);
        return new TenantPlatformSafety(dispatch,reconciliation);
    }

    private Backlog backlog(String sql){return jdbc.queryForObject(sql,(rs,row)->new Backlog(rs.getLong(1),Math.max(0L,rs.getLong(2))));}
    private long count(String sql,Object...args){Long value=jdbc.queryForObject(sql,Long.class,args);return value==null?0L:Math.max(0L,value);}
    private void bind(String tenant,String actor){jdbc.queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor);}
    private String serialize(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("CONFIGURATION_CUTOVER_WAVE_SAFETY_EVIDENCE_SERIALIZATION_FAILED",ex);}}
    private static boolean requiresSafety(String waveId){return W4.equals(waveId)||W5.equals(waveId)||W6.equals(waveId);}
    private static String profile(String waveId){if(W4.equals(waveId))return PROFILE_TASK_DISPATCH;if(W5.equals(waveId))return PROFILE_EXTERNAL_INTEGRATION_A2A;if(W6.equals(waveId))return PROFILE_PLATFORM_READINESS_RECOVERY;return null;}
    private static List<String> blockers(boolean required,RuntimeConfigurationCutoverWaveSafetyAttestation latest,OffsetDateTime now){
        if(!required)return List.of();List<String> out=new ArrayList<>();
        if(latest==null)out.add("SAFETY_ATTESTATION_REQUIRED");
        else {if(!"PASS".equals(latest.status()))out.add("SAFETY_ATTESTATION_NOT_PASS:"+latest.status());if(!latest.expiresAt().isAfter(now))out.add("SAFETY_ATTESTATION_EXPIRED:"+latest.expiresAt());}
        return List.copyOf(out);
    }
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
    private static String text(String value){return value==null?"":value.trim();}
    private record Backlog(long count,long oldestMillis){}
    private record TenantTaskSafety(Backlog dispatch,Backlog reconciliation,long staleRunningTasks,long manualRecoveryTasks,long expiredFinalizationClaims,long overdueTaskConditions,long authorityMismatches){}
    private record TenantExternalSafety(Backlog integrationOutbox,Backlog a2aReconciliation,long issuePermanentFailures,long deadLetters,long conflicts,long openCircuits,long waitHuman,long expiredA2AClaims,long unhealthyApprovedInterfaces){}
    private record TenantPlatformSafety(Backlog dispatch,Backlog reconciliation){}
    public record AttestationStatus(String waveId,boolean required,String profile,RuntimeConfigurationCutoverWaveSafetyAttestation latestAttestation,List<String> blockers){}
}
