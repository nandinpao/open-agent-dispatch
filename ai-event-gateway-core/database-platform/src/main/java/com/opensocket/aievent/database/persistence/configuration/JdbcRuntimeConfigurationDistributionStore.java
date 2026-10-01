package com.opensocket.aievent.database.persistence.configuration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationApplyStatus;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationDistributionOutboxEntry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationDistributionStore;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationNodeApplyState;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationOutboxStatus;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationRequiredNodeTarget;

/** PostgreSQL implementation of durable distribution, topology-target, and apply-state truth. */
@DatabaseRepositoryAdapter
public class JdbcRuntimeConfigurationDistributionStore implements RuntimeConfigurationDistributionStore {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcRuntimeConfigurationDistributionStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc=jdbc;
    }

    @Override
    public List<String> listActiveConfigSetIds(String environment) {
        return jdbc.query("""
            select s.config_set_id
              from runtime_config_sets s
              join runtime_config_active_revisions a on a.config_set_id=s.config_set_id
             where s.environment=:environment and s.status='ACTIVE'
             order by s.set_key
            """,new MapSqlParameterSource("environment",required(environment,"environment").toUpperCase()),(rs,row)->rs.getString(1));
    }

    @Override
    public List<RuntimeConfigurationDistributionOutboxEntry> claimDue(String workerId,int limit,Duration lease) {
        String worker=required(workerId,"workerId");
        long leaseSeconds=Math.max(5,lease == null ? 30 : lease.toSeconds());
        int batch=Math.max(1,Math.min(limit,500));
        return jdbc.query("""
            with due as (
              select config_set_id,revision_id
                from runtime_config_outbox
               where status in ('PENDING','FAILED')
                 and next_attempt_at <= now()
                 and (lease_until is null or lease_until < now())
               order by created_at
               for update skip locked
               limit :limit
            )
            update runtime_config_outbox o
               set status='CLAIMED',attempt_count=o.attempt_count+1,lease_owner=:worker,
                   lease_until=now()+(:leaseSeconds * interval '1 second'),last_error=null
              from due d
             where o.config_set_id=d.config_set_id and o.revision_id=d.revision_id
            returning o.config_set_id,o.revision_id,o.environment,o.status,o.attempt_count,o.next_attempt_at,
                      o.lease_owner,o.lease_until,o.payload_fingerprint,o.last_error,o.created_at,o.distributed_at
            """,new MapSqlParameterSource("limit",batch).addValue("worker",worker).addValue("leaseSeconds",leaseSeconds),OUTBOX_MAPPER);
    }

    @Override
    public void markDistributed(String configSetId,String revisionId,String workerId,String payloadFingerprint) {
        int updated=jdbc.update("""
            update runtime_config_outbox
               set status='DISTRIBUTED',payload_fingerprint=:fingerprint,distributed_at=now(),
                   lease_owner=null,lease_until=null,last_error=null
             where config_set_id=:setId and revision_id=:revisionId and status='CLAIMED' and lease_owner=:worker
            """,base(configSetId,revisionId).addValue("worker",required(workerId,"workerId"))
                    .addValue("fingerprint",sha256(payloadFingerprint)));
        if(updated!=1) throw new IllegalStateException("Runtime configuration outbox claim lost before DISTRIBUTED: "+configSetId+"/"+revisionId);
    }

    @Override
    public void markSuperseded(String configSetId,String revisionId,String workerId) {
        int updated=jdbc.update("""
            update runtime_config_outbox
               set status='SUPERSEDED',lease_owner=null,lease_until=null,last_error=null
             where config_set_id=:setId and revision_id=:revisionId and status='CLAIMED' and lease_owner=:worker
            """,base(configSetId,revisionId).addValue("worker",required(workerId,"workerId")));
        if(updated!=1) throw new IllegalStateException("Runtime configuration outbox claim lost before SUPERSEDED: "+configSetId+"/"+revisionId);
    }

    @Override
    public void markFailed(String configSetId,String revisionId,String workerId,OffsetDateTime retryAt,String error) {
        int updated=jdbc.update("""
            update runtime_config_outbox
               set status='FAILED',next_attempt_at=:retryAt,last_error=:error,lease_owner=null,lease_until=null
             where config_set_id=:setId and revision_id=:revisionId and status='CLAIMED' and lease_owner=:worker
            """,base(configSetId,revisionId).addValue("worker",required(workerId,"workerId"))
                    .addValue("retryAt",retryAt==null?OffsetDateTime.now().plusSeconds(10):retryAt)
                    .addValue("error",clip(error,2000)));
        if(updated!=1) throw new IllegalStateException("Runtime configuration outbox claim lost before FAILED: "+configSetId+"/"+revisionId);
    }

    @Override
    public void requestRedistribution(String configSetId,String revisionId,String reason) {
        String setId=required(configSetId,"configSetId");
        String rev=required(revisionId,"revisionId");
        int outbox=jdbc.update("""
            update runtime_config_outbox
               set status='PENDING',next_attempt_at=now(),lease_owner=null,lease_until=null,last_error=:reason,distributed_at=null
             where config_set_id=:setId and revision_id=:revisionId and status in ('DISTRIBUTED','FAILED','SUPERSEDED','PENDING')
            """,base(setId,rev).addValue("reason",clip(reason,2000)));
        if(outbox==0) {
            jdbc.update("""
                insert into runtime_config_outbox(config_set_id,revision_id,environment,status,next_attempt_at,last_error)
                select s.config_set_id,:revisionId,s.environment,'PENDING',now(),:reason
                  from runtime_config_sets s where s.config_set_id=:setId
                on conflict(config_set_id,revision_id) do update set status='PENDING',next_attempt_at=now(),
                  lease_owner=null,lease_until=null,last_error=excluded.last_error,distributed_at=null
                """,base(setId,rev).addValue("reason",clip(reason,2000)));
        }
        // Desired-state publication is control-plane activity and MUST NOT manufacture node presence.
        jdbc.update("""
            update runtime_config_apply_states
               set state='DESIRED',desired_revision_id=:revisionId,distributed_at=null,
                   error_code=null,error_detail=null
             where config_set_id=:setId
            """,base(setId,rev));
    }

    @Override
    public void registerRequiredTarget(String configSetId,String nodeId,String nodeRole,String nodeInstanceId,String registrationSource,int supportedAuthorityContractVersion) {
        jdbc.update("""
            insert into runtime_config_required_node_targets(
              config_set_id,node_id,node_role,node_instance_id,required,registration_source,supported_authority_contract_version,registered_at,updated_at)
            values(:setId,:nodeId,:nodeRole,:nodeInstanceId,true,:source,:contractVersion,now(),now())
            on conflict(config_set_id,node_id) do update set
              node_role=excluded.node_role,required=true,
              supported_authority_contract_version=case
                when runtime_config_required_node_targets.node_instance_id is distinct from excluded.node_instance_id
                  then excluded.supported_authority_contract_version
                when :capabilityExplicit then excluded.supported_authority_contract_version
                else runtime_config_required_node_targets.supported_authority_contract_version end,
              node_instance_id=excluded.node_instance_id,
              registration_source=case
                when runtime_config_required_node_targets.registration_source='V40_8_3_BACKFILL' then excluded.registration_source
                else runtime_config_required_node_targets.registration_source end,
              updated_at=now()
            """,targetParams(configSetId,nodeId,nodeRole,nodeInstanceId).addValue("source",required(registrationSource,"registrationSource"))
                    .addValue("contractVersion",storedContractVersion(supportedAuthorityContractVersion))
                    .addValue("capabilityExplicit",supportedAuthorityContractVersion>0));
    }

    @Override
    public List<RuntimeConfigurationRequiredNodeTarget> listRequiredTargets(String configSetId) {
        return jdbc.query("""
            select config_set_id,node_id,node_role,node_instance_id,required,registration_source,supported_authority_contract_version,registered_at,last_seen_at,updated_at
              from runtime_config_required_node_targets
             where config_set_id=:setId and required=true
             order by node_role,node_id
            """,new MapSqlParameterSource("setId",required(configSetId,"configSetId")),TARGET_MAPPER);
    }

    @Override
    public List<RuntimeConfigurationNodeApplyState> listRequiredApplyStates(String configSetId) {
        return jdbc.query("""
            select t.config_set_id,t.node_id,t.node_role,t.node_instance_id,
                   a.desired_revision_id,a.applied_revision_id,coalesce(a.state,'NOT_SEEN') state,
                   a.snapshot_fingerprint,a.desired_at,a.distributed_at,a.applied_at,
                   coalesce(t.last_seen_at,a.last_seen_at) last_seen_at,a.error_code,a.error_detail,
                   a.authority_runtime_state,a.snapshot_expires_at,a.authority_observed_at
              from runtime_config_required_node_targets t
              left join runtime_config_apply_states a
                on a.config_set_id=t.config_set_id and a.node_id=t.node_id
             where t.config_set_id=:setId and t.required=true
             order by t.node_role,t.node_id
            """,new MapSqlParameterSource("setId",required(configSetId,"configSetId")),APPLY_MAPPER);
    }

    @Override
    public int markStaleRequiredNodes(String environment,Duration staleAfter) {
        long staleSeconds=Math.max(10,staleAfter==null?60:staleAfter.toSeconds());
        return jdbc.update("""
            update runtime_config_apply_states a
               set state='STALE',error_code='NODE_STALE',
                   error_detail='Required node has not reconciled within configured stale threshold'
              from runtime_config_required_node_targets t
              join runtime_config_sets s on s.config_set_id=t.config_set_id
             where a.config_set_id=t.config_set_id and a.node_id=t.node_id
               and t.required=true and s.environment=:environment
               and t.last_seen_at is not null
               and t.last_seen_at < now()-(:staleSeconds * interval '1 second')
               and a.state <> 'STALE'
            """,new MapSqlParameterSource("environment",required(environment,"environment").toUpperCase())
                    .addValue("staleSeconds",staleSeconds));
    }

    @Override
    public void recordDesired(String configSetId,String nodeId,String nodeRole,String nodeInstanceId,
            String desiredRevisionId,String snapshotFingerprint,boolean distributed) {
        registerRequiredTarget(configSetId,nodeId,nodeRole,nodeInstanceId,"RUNTIME_OBSERVED_NODE",0);
        touchTarget(configSetId,nodeId,nodeRole,nodeInstanceId);
        jdbc.update("""
            insert into runtime_config_apply_states(config_set_id,node_id,node_role,node_instance_id,desired_revision_id,
              applied_revision_id,state,snapshot_fingerprint,desired_at,distributed_at,last_seen_at)
            values(:setId,:nodeId,:nodeRole,:nodeInstanceId,:desiredRevisionId,null,:state,:fingerprint,now(),
              case when :distributed then now() else null end,now())
            on conflict(config_set_id,node_id) do update set
              node_role=excluded.node_role,node_instance_id=excluded.node_instance_id,
              desired_revision_id=excluded.desired_revision_id,
              state=case when runtime_config_apply_states.state='APPLIED'
                              and runtime_config_apply_states.applied_revision_id=excluded.desired_revision_id
                              and runtime_config_apply_states.snapshot_fingerprint=excluded.snapshot_fingerprint
                         then 'APPLIED' else excluded.state end,
              snapshot_fingerprint=excluded.snapshot_fingerprint,
              desired_at=case when runtime_config_apply_states.desired_revision_id is distinct from excluded.desired_revision_id
                                   or runtime_config_apply_states.snapshot_fingerprint is distinct from excluded.snapshot_fingerprint
                              then now() else runtime_config_apply_states.desired_at end,
              distributed_at=case when :distributed then now() else runtime_config_apply_states.distributed_at end,
              last_seen_at=now(),error_code=null,error_detail=null
            """,nodeParams(configSetId,nodeId,nodeRole,nodeInstanceId,desiredRevisionId,snapshotFingerprint)
                    .addValue("state",distributed?"DISTRIBUTED":"DESIRED").addValue("distributed",distributed));
    }

    @Override
    public void acknowledgeApplied(String configSetId,String nodeId,String nodeRole,String nodeInstanceId,
            String desiredRevisionId,String appliedRevisionId,String snapshotFingerprint) {
        if(!required(desiredRevisionId,"desiredRevisionId").equals(required(appliedRevisionId,"appliedRevisionId")))
            throw new IllegalArgumentException("APPLIED ACK revision must equal desired revision");
        registerRequiredTarget(configSetId,nodeId,nodeRole,nodeInstanceId,"RUNTIME_OBSERVED_NODE",0);
        touchTarget(configSetId,nodeId,nodeRole,nodeInstanceId);
        jdbc.update("""
            insert into runtime_config_apply_states(config_set_id,node_id,node_role,node_instance_id,desired_revision_id,
              applied_revision_id,state,snapshot_fingerprint,desired_at,distributed_at,applied_at,last_seen_at)
            values(:setId,:nodeId,:nodeRole,:nodeInstanceId,:desiredRevisionId,:appliedRevisionId,'APPLIED',:fingerprint,now(),now(),now(),now())
            on conflict(config_set_id,node_id) do update set
              node_role=excluded.node_role,node_instance_id=excluded.node_instance_id,
              desired_revision_id=excluded.desired_revision_id,applied_revision_id=excluded.applied_revision_id,
              state='APPLIED',snapshot_fingerprint=excluded.snapshot_fingerprint,
              distributed_at=coalesce(runtime_config_apply_states.distributed_at,now()),applied_at=now(),last_seen_at=now(),
              error_code=null,error_detail=null
            """,nodeParams(configSetId,nodeId,nodeRole,nodeInstanceId,desiredRevisionId,snapshotFingerprint)
                    .addValue("appliedRevisionId",appliedRevisionId));
    }

    @Override
    public void acknowledgeFailed(String configSetId,String nodeId,String nodeRole,String nodeInstanceId,
            String desiredRevisionId,String appliedRevisionId,String snapshotFingerprint,String errorCode,String errorDetail) {
        registerRequiredTarget(configSetId,nodeId,nodeRole,nodeInstanceId,"RUNTIME_OBSERVED_NODE",0);
        touchTarget(configSetId,nodeId,nodeRole,nodeInstanceId);
        jdbc.update("""
            insert into runtime_config_apply_states(config_set_id,node_id,node_role,node_instance_id,desired_revision_id,
              applied_revision_id,state,snapshot_fingerprint,desired_at,last_seen_at,error_code,error_detail)
            values(:setId,:nodeId,:nodeRole,:nodeInstanceId,:desiredRevisionId,:appliedRevisionId,'FAILED',:fingerprint,now(),now(),:errorCode,:errorDetail)
            on conflict(config_set_id,node_id) do update set
              node_role=excluded.node_role,node_instance_id=excluded.node_instance_id,
              desired_revision_id=excluded.desired_revision_id,applied_revision_id=excluded.applied_revision_id,
              state='FAILED',snapshot_fingerprint=excluded.snapshot_fingerprint,last_seen_at=now(),
              error_code=excluded.error_code,error_detail=excluded.error_detail
            """,nodeParams(configSetId,nodeId,nodeRole,nodeInstanceId,desiredRevisionId,snapshotFingerprint)
                    .addValue("appliedRevisionId",text(appliedRevisionId)).addValue("errorCode",clip(errorCode,128)).addValue("errorDetail",clip(errorDetail,2000)));
    }

    @Override
    public void recordAuthorityHealth(String configSetId,String nodeId,String nodeRole,String nodeInstanceId,
            String authorityRuntimeState,OffsetDateTime snapshotExpiresAt) {
        String authorityState=authorityState(authorityRuntimeState);
        registerRequiredTarget(configSetId,nodeId,nodeRole,nodeInstanceId,"RUNTIME_AUTHORITY_HEALTH",0);
        touchTarget(configSetId,nodeId,nodeRole,nodeInstanceId);
        jdbc.update("""
            update runtime_config_apply_states
               set node_role=:nodeRole,node_instance_id=:nodeInstanceId,
                   authority_runtime_state=:authorityState,snapshot_expires_at=:snapshotExpiresAt,
                   authority_observed_at=now(),last_seen_at=now()
             where config_set_id=:setId and node_id=:nodeId
            """,targetParams(configSetId,nodeId,nodeRole,nodeInstanceId)
                    .addValue("authorityState",authorityState).addValue("snapshotExpiresAt",snapshotExpiresAt));
    }

    @Override public Optional<RuntimeConfigurationNodeApplyState> findApplyState(String configSetId,String nodeId) {
        return jdbc.query(APPLY_SELECT+" where config_set_id=:setId and node_id=:nodeId",
                new MapSqlParameterSource("setId",required(configSetId,"configSetId")).addValue("nodeId",required(nodeId,"nodeId")),APPLY_MAPPER)
                .stream().findFirst();
    }

    @Override public List<RuntimeConfigurationNodeApplyState> listApplyStates(String configSetId) {
        return jdbc.query(APPLY_SELECT+" where config_set_id=:setId order by node_role,node_id",
                new MapSqlParameterSource("setId",required(configSetId,"configSetId")),APPLY_MAPPER);
    }

    private void touchTarget(String configSetId,String nodeId,String nodeRole,String nodeInstanceId) {
        jdbc.update("""
            update runtime_config_required_node_targets
               set node_role=:nodeRole,node_instance_id=:nodeInstanceId,last_seen_at=now(),updated_at=now()
             where config_set_id=:setId and node_id=:nodeId
            """,targetParams(configSetId,nodeId,nodeRole,nodeInstanceId));
    }

    private static MapSqlParameterSource base(String setId,String revisionId) {
        return new MapSqlParameterSource("setId",required(setId,"configSetId")).addValue("revisionId",required(revisionId,"revisionId"));
    }
    private static MapSqlParameterSource targetParams(String setId,String nodeId,String nodeRole,String nodeInstanceId) {
        return new MapSqlParameterSource("setId",required(setId,"configSetId")).addValue("nodeId",required(nodeId,"nodeId"))
                .addValue("nodeRole",role(nodeRole)).addValue("nodeInstanceId",required(nodeInstanceId,"nodeInstanceId"));
    }
    private static MapSqlParameterSource nodeParams(String setId,String nodeId,String nodeRole,String nodeInstanceId,String desiredRevisionId,String fingerprint) {
        return targetParams(setId,nodeId,nodeRole,nodeInstanceId)
                .addValue("desiredRevisionId",required(desiredRevisionId,"desiredRevisionId")).addValue("fingerprint",sha256(fingerprint));
    }
    private static int storedContractVersion(int value) { if(value<0||value>100) throw new IllegalArgumentException("supportedAuthorityContractVersion must be between 0 and 100"); return value==0?1:value; }
    private static String authorityState(String value) {
        String state=required(value,"authorityRuntimeState").toUpperCase();
        if(!java.util.Set.of("ACTIVE","STALE_LKG","EXPIRED","INVALID","MISSING").contains(state))
            throw new IllegalArgumentException("Unsupported Runtime Configuration authority state: "+value);
        return state;
    }
    private static String role(String value) {
        String role=required(value,"nodeRole").toUpperCase();
        if(!role.equals("CORE")&&!role.equals("GATEWAY")&&!role.equals("WORKER")) throw new IllegalArgumentException("Unsupported runtime configuration node role: "+value);
        return role;
    }
    private static String sha256(String value) {
        String v=required(value,"snapshotFingerprint");
        if(!v.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("snapshotFingerprint must be SHA-256 hex");
        return v.toLowerCase();
    }
    private static String required(String value,String field) { if(value==null||value.isBlank()) throw new IllegalArgumentException(field+" is required"); return value.trim(); }
    private static String text(String value) { return value==null||value.isBlank()?null:value.trim(); }
    private static String clip(String value,int max) { String v=text(value); return v==null?null:v.substring(0,Math.min(v.length(),max)); }

    private static final RowMapper<RuntimeConfigurationDistributionOutboxEntry> OUTBOX_MAPPER=(rs,row)->new RuntimeConfigurationDistributionOutboxEntry(
            rs.getString("config_set_id"),rs.getString("revision_id"),rs.getString("environment"),
            RuntimeConfigurationOutboxStatus.valueOf(rs.getString("status")),rs.getInt("attempt_count"),
            rs.getObject("next_attempt_at",OffsetDateTime.class),rs.getString("lease_owner"),rs.getObject("lease_until",OffsetDateTime.class),
            rs.getString("payload_fingerprint"),rs.getString("last_error"),rs.getObject("created_at",OffsetDateTime.class),
            rs.getObject("distributed_at",OffsetDateTime.class));
    private static final String APPLY_SELECT="""
        select config_set_id,node_id,node_role,node_instance_id,desired_revision_id,applied_revision_id,state,snapshot_fingerprint,
               desired_at,distributed_at,applied_at,last_seen_at,error_code,error_detail,
               authority_runtime_state,snapshot_expires_at,authority_observed_at from runtime_config_apply_states
        """;
    private static final RowMapper<RuntimeConfigurationNodeApplyState> APPLY_MAPPER=new RowMapper<>() {
        @Override public RuntimeConfigurationNodeApplyState mapRow(ResultSet rs,int rowNum)throws SQLException {
            return new RuntimeConfigurationNodeApplyState(rs.getString("config_set_id"),rs.getString("node_id"),rs.getString("node_role"),
                    rs.getString("node_instance_id"),rs.getString("desired_revision_id"),rs.getString("applied_revision_id"),
                    RuntimeConfigurationApplyStatus.valueOf(rs.getString("state")),rs.getString("snapshot_fingerprint"),
                    rs.getObject("desired_at",OffsetDateTime.class),rs.getObject("distributed_at",OffsetDateTime.class),
                    rs.getObject("applied_at",OffsetDateTime.class),rs.getObject("last_seen_at",OffsetDateTime.class),
                    rs.getString("error_code"),rs.getString("error_detail"),rs.getString("authority_runtime_state"),
                    rs.getObject("snapshot_expires_at",OffsetDateTime.class),rs.getObject("authority_observed_at",OffsetDateTime.class));
        }
    };
    private static final RowMapper<RuntimeConfigurationRequiredNodeTarget> TARGET_MAPPER=(rs,row)->new RuntimeConfigurationRequiredNodeTarget(
            rs.getString("config_set_id"),rs.getString("node_id"),rs.getString("node_role"),rs.getString("node_instance_id"),
            rs.getBoolean("required"),rs.getString("registration_source"),rs.getInt("supported_authority_contract_version"),rs.getObject("registered_at",OffsetDateTime.class),
            rs.getObject("last_seen_at",OffsetDateTime.class),rs.getObject("updated_at",OffsetDateTime.class));
}
