package com.opensocket.aievent.database.persistence.configuration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverPlan;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverState;
import com.opensocket.aievent.core.kernel.configuration.cutover.RuntimeConfigurationCutoverStore;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** PostgreSQL persistence for durable two-phase Runtime Configuration authority cutover. */
@DatabaseRepositoryAdapter
public class JdbcRuntimeConfigurationCutoverStore implements RuntimeConfigurationCutoverStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcRuntimeConfigurationCutoverStore(NamedParameterJdbcTemplate jdbc,ObjectMapper json) {
        this.jdbc=jdbc;this.json=json;
    }

    @Override
    public Optional<RuntimeConfigurationCutoverPlan> findPrepared(String configSetId) {
        return jdbc.query(SELECT+" where config_set_id=:setId and state='PREPARED' order by prepared_at desc limit 1",
                new MapSqlParameterSource("setId",required(configSetId,"configSetId")),MAPPER).stream().findFirst();
    }

    @Override
    public Optional<RuntimeConfigurationCutoverPlan> findLatest(String configSetId) {
        return jdbc.query(SELECT+" where config_set_id=:setId order by prepared_at desc limit 1",
                new MapSqlParameterSource("setId",required(configSetId,"configSetId")),MAPPER).stream().findFirst();
    }

    @Override
    public RuntimeConfigurationCutoverPlan createPrepared(RuntimeConfigurationCutoverPlan plan) {
        if(plan.state()!=RuntimeConfigurationCutoverState.PREPARED)throw new IllegalArgumentException("New cutover plan must be PREPARED");
        jdbc.update("""
            insert into runtime_config_cutovers(cutover_id,config_set_id,set_key,environment,revision_id,
              authority_contract_version,target_authority_mode,required_keys_json,expected_snapshot_fingerprint,state,
              requested_by,reason,prepared_at,version,updated_at)
            values(:cutoverId,:setId,:setKey,:environment,:revisionId,:contractVersion,:authorityMode,
              cast(:requiredKeys as jsonb),:fingerprint,'PREPARED',:requestedBy,:reason,:preparedAt,1,now())
            """,params(plan));
        return findPrepared(plan.configSetId()).orElseThrow();
    }

    @Override
    public RuntimeConfigurationCutoverPlan markFinalized(String cutoverId,long expectedVersion,String actor,String reason) {
        return transition(cutoverId,expectedVersion,"FINALIZED","finalized_at",actor,reason);
    }

    @Override
    public RuntimeConfigurationCutoverPlan markCancelled(String cutoverId,long expectedVersion,String actor,String reason) {
        return transition(cutoverId,expectedVersion,"CANCELLED","cancelled_at",actor,reason);
    }

    private RuntimeConfigurationCutoverPlan transition(String cutoverId,long expectedVersion,String state,String timestampColumn,String actor,String reason) {
        MapSqlParameterSource p=new MapSqlParameterSource("cutoverId",required(cutoverId,"cutoverId"))
                .addValue("version",expectedVersion).addValue("actor",required(actor,"actor")).addValue("reason",required(reason,"reason"));
        int updated=jdbc.update("update runtime_config_cutovers set state='"+state+"',"+timestampColumn+"=now(),version=version+1,updated_at=now(),reason=:reason where cutover_id=:cutoverId and state='PREPARED' and version=:version",p);
        if(updated!=1)throw new IllegalStateException("CONFIGURATION_CUTOVER_CONCURRENT_UPDATE cutoverId="+cutoverId);
        return jdbc.query(SELECT+" where cutover_id=:cutoverId",p,MAPPER).stream().findFirst().orElseThrow();
    }

    private MapSqlParameterSource params(RuntimeConfigurationCutoverPlan p) {
        return new MapSqlParameterSource("cutoverId",required(p.cutoverId(),"cutoverId"))
                .addValue("setId",required(p.configSetId(),"configSetId")).addValue("setKey",required(p.setKey(),"setKey"))
                .addValue("environment",required(p.environment(),"environment")).addValue("revisionId",required(p.revisionId(),"revisionId"))
                .addValue("contractVersion",p.authorityContractVersion()).addValue("authorityMode",required(p.targetAuthorityMode(),"targetAuthorityMode"))
                .addValue("requiredKeys",writeKeys(p.requiredKeys())).addValue("fingerprint",required(p.expectedSnapshotFingerprint(),"expectedSnapshotFingerprint").toLowerCase())
                .addValue("requestedBy",required(p.requestedBy(),"requestedBy")).addValue("reason",required(p.reason(),"reason"))
                .addValue("preparedAt",p.preparedAt()==null?OffsetDateTime.now():p.preparedAt());
    }

    private String writeKeys(Set<String> keys){try{return json.writeValueAsString(new java.util.TreeSet<>(keys));}catch(Exception ex){throw new IllegalStateException("Unable to encode cutover required keys",ex);}}
    private Set<String> readKeys(String value){try{JsonNode n=json.readTree(value);LinkedHashSet<String> out=new LinkedHashSet<>();if(n!=null&&n.isArray())n.forEach(v->out.add(v.asText()));return Set.copyOf(out);}catch(Exception ex){throw new IllegalStateException("Unable to decode cutover required keys",ex);}}
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}

    private static final String SELECT="""
        select cutover_id,config_set_id,set_key,environment,revision_id,authority_contract_version,target_authority_mode,
               required_keys_json::text required_keys_json,expected_snapshot_fingerprint,state,requested_by,reason,
               prepared_at,finalized_at,cancelled_at,version
          from runtime_config_cutovers
        """;

    private final RowMapper<RuntimeConfigurationCutoverPlan> MAPPER=new RowMapper<>(){
        @Override public RuntimeConfigurationCutoverPlan mapRow(ResultSet rs,int rowNum)throws SQLException{
            return new RuntimeConfigurationCutoverPlan(rs.getString("cutover_id"),rs.getString("config_set_id"),rs.getString("set_key"),
                    rs.getString("environment"),rs.getString("revision_id"),rs.getInt("authority_contract_version"),rs.getString("target_authority_mode"),
                    readKeys(rs.getString("required_keys_json")),rs.getString("expected_snapshot_fingerprint"),RuntimeConfigurationCutoverState.valueOf(rs.getString("state")),
                    rs.getString("requested_by"),rs.getString("reason"),rs.getObject("prepared_at",OffsetDateTime.class),
                    rs.getObject("finalized_at",OffsetDateTime.class),rs.getObject("cancelled_at",OffsetDateTime.class),rs.getLong("version"));
        }
    };
}
