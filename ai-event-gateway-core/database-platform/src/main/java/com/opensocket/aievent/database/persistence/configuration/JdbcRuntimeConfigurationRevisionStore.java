package com.opensocket.aievent.database.persistence.configuration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.opensocket.aievent.core.kernel.configuration.ConfigurationScope;
import com.opensocket.aievent.core.kernel.configuration.OpenDispatchEnvironment;
import com.opensocket.aievent.core.kernel.configuration.revision.ConfigurationRevisionConflictException;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationAuditEntry;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationConfigSet;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevision;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionItem;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionState;
import com.opensocket.aievent.core.kernel.configuration.revision.RuntimeConfigurationRevisionStore;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

/** V40-3 PostgreSQL adapter. Distribution/outbox/apply-state intentionally do not exist yet. */
@DatabaseRepositoryAdapter
public class JdbcRuntimeConfigurationRevisionStore implements RuntimeConfigurationRevisionStore {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcRuntimeConfigurationRevisionStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RuntimeConfigurationConfigSet createConfigSet(String configSetId,String setKey,
            OpenDispatchEnvironment environment,ConfigurationScope scope,String scopeRef,
            String ownerComponent,String actor) {
        if (!scope.genericRuntimeConfigSupported()) throw new IllegalArgumentException("Unsupported generic runtime configuration scope: "+scope);
        jdbc.update("""
            insert into runtime_config_sets(config_set_id,set_key,environment,scope,scope_ref,owner_component,status,
              resource_version,created_by,updated_by)
            values(:id,:setKey,:environment,:scope,:scopeRef,:owner,'ACTIVE',1,:actor,:actor)
            """,new MapSqlParameterSource().addValue("id",required(configSetId,"configSetId"))
                .addValue("setKey",required(setKey,"setKey")).addValue("environment",environment.name())
                .addValue("scope",scope.name()).addValue("scopeRef",text(scopeRef)).addValue("owner",required(ownerComponent,"ownerComponent"))
                .addValue("actor",required(actor,"actor")));
        audit(configSetId,null,"CONFIG_SET_CREATED",actor,"Config Set created",null,"{}");
        return findConfigSet(configSetId).orElseThrow();
    }

    @Override public Optional<RuntimeConfigurationConfigSet> findConfigSet(String configSetId) {
        return jdbc.query("""
            select config_set_id,set_key,environment,scope,scope_ref,owner_component,status,resource_version,created_at,updated_at
              from runtime_config_sets where config_set_id=:id
            """,new MapSqlParameterSource("id",required(configSetId,"configSetId")),CONFIG_SET_MAPPER).stream().findFirst();
    }

    @Override public Optional<RuntimeConfigurationConfigSet> findConfigSetBySetKey(OpenDispatchEnvironment environment,String setKey) {
        return jdbc.query("""
            select config_set_id,set_key,environment,scope,scope_ref,owner_component,status,resource_version,created_at,updated_at
              from runtime_config_sets where environment=:environment and set_key=:setKey
            """,new MapSqlParameterSource("environment",environment.name()).addValue("setKey",required(setKey,"setKey")),CONFIG_SET_MAPPER).stream().findFirst();
    }

    @Override public Optional<RuntimeConfigurationRevision> findRevision(String revisionId) {
        return jdbc.query(REVISION_SELECT+" where revision_id=:id",new MapSqlParameterSource("id",required(revisionId,"revisionId")),REVISION_MAPPER).stream().findFirst();
    }

    @Override public List<RuntimeConfigurationRevision> listRevisions(String configSetId,int limit) {
        return jdbc.query(REVISION_SELECT+" where config_set_id=:id order by sequence_no desc limit :limit",
            new MapSqlParameterSource("id",required(configSetId,"configSetId")).addValue("limit",Math.max(1,Math.min(limit,200))),REVISION_MAPPER);
    }

    @Override public Optional<String> findActiveRevisionId(String configSetId) {
        return jdbc.query("select revision_id from runtime_config_active_revisions where config_set_id=:id",
            new MapSqlParameterSource("id",required(configSetId,"configSetId")),(rs,row)->rs.getString(1)).stream().findFirst();
    }

    @Override public List<RuntimeConfigurationRevisionItem> listItems(String revisionId) {
        return jdbc.query("""
            select revision_id,definition_key,value_json::text,value_fingerprint,created_by,created_at,updated_by,updated_at
              from runtime_config_revision_items where revision_id=:id order by definition_key
            """,new MapSqlParameterSource("id",required(revisionId,"revisionId")),ITEM_MAPPER);
    }

    @Override public List<RuntimeConfigurationAuditEntry> history(String configSetId,int limit) {
        return jdbc.query("""
            select audit_id,config_set_id,revision_id,action,actor,reason,correlation_id,metadata_json::text,created_at
              from runtime_config_audit_logs where config_set_id=:id order by created_at desc limit :limit
            """,new MapSqlParameterSource("id",required(configSetId,"configSetId")).addValue("limit",Math.max(1,Math.min(limit,1000))),AUDIT_MAPPER);
    }

    @Override
    public RuntimeConfigurationRevision createDraft(String revisionId,String configSetId,String actor,String reason,
            String rollbackOfRevisionId,String restoreSourceRevisionId,String correlationId) {
        lockConfigSet(configSetId);
        String base=findActiveRevisionId(configSetId).orElse(null);
        long sequence=nextSequence(configSetId);
        jdbc.update("""
            insert into runtime_config_revisions(revision_id,config_set_id,sequence_no,base_revision_id,
              rollback_of_revision_id,restore_source_revision_id,state,definition_schema_version,reason,created_by,updated_by)
            values(:revisionId,:setId,:sequence,:base,:rollbackOf,:restoreSource,'DRAFT',1,:reason,:actor,:actor)
            """,new MapSqlParameterSource().addValue("revisionId",required(revisionId,"revisionId"))
              .addValue("setId",required(configSetId,"configSetId")).addValue("sequence",sequence).addValue("base",base)
              .addValue("rollbackOf",text(rollbackOfRevisionId)).addValue("restoreSource",text(restoreSourceRevisionId))
              .addValue("reason",required(reason,"reason")).addValue("actor",required(actor,"actor")));
        audit(configSetId,revisionId,"REVISION_DRAFT_CREATED",actor,reason,correlationId,
            json("baseRevisionId",base,"sequenceNo",sequence));
        return findRevision(revisionId).orElseThrow();
    }

    @Override
    public RuntimeConfigurationRevisionItem putDraftValue(String revisionId,String definitionKey,String canonicalJson,
            String valueFingerprint,String actor,String reason,String correlationId) {
        RuntimeConfigurationRevision revision=requireRevision(revisionId);
        if (revision.state()!=RuntimeConfigurationRevisionState.DRAFT) throw new IllegalStateException("Only DRAFT revision values are editable");
        jdbc.update("""
            insert into runtime_config_revision_items(revision_id,definition_key,value_json,value_fingerprint,created_by,updated_by)
            values(:revisionId,:key,cast(:value as jsonb),:fingerprint,:actor,:actor)
            on conflict(revision_id,definition_key) do update set value_json=excluded.value_json,
              value_fingerprint=excluded.value_fingerprint,updated_by=excluded.updated_by,updated_at=now()
            """,new MapSqlParameterSource().addValue("revisionId",revisionId).addValue("key",required(definitionKey,"definitionKey"))
              .addValue("value",required(canonicalJson,"canonicalJson")).addValue("fingerprint",required(valueFingerprint,"valueFingerprint"))
              .addValue("actor",required(actor,"actor")));
        audit(revision.configSetId(),revisionId,"REVISION_VALUE_PUT",actor,reason,correlationId,json("definitionKey",definitionKey));
        return listItems(revisionId).stream().filter(v->v.definitionKey().equals(definitionKey)).findFirst().orElseThrow();
    }

    @Override
    public RuntimeConfigurationRevision transition(String revisionId,RuntimeConfigurationRevisionState expected,
            RuntimeConfigurationRevisionState target,String actor,String reason,String correlationId) {
        if (target==RuntimeConfigurationRevisionState.PUBLISHED || target==RuntimeConfigurationRevisionState.SUPERSEDED)
            throw new IllegalArgumentException("Use atomic publish operation for PUBLISHED/SUPERSEDED transitions");
        MapSqlParameterSource p=new MapSqlParameterSource().addValue("id",required(revisionId,"revisionId"))
            .addValue("expected",expected.name()).addValue("target",target.name()).addValue("actor",required(actor,"actor"));
        String lifecycle="";
        if(target==RuntimeConfigurationRevisionState.VALIDATED) lifecycle=",validated_at=now(),validated_by=:actor";
        else if(target==RuntimeConfigurationRevisionState.PENDING_APPROVAL) lifecycle=",submitted_at=now(),submitted_by=:actor";
        else if(target==RuntimeConfigurationRevisionState.APPROVED) lifecycle=",approved_at=now(),approved_by=:actor";
        int updated=jdbc.update("update runtime_config_revisions set state=:target,updated_by=:actor"+lifecycle+" where revision_id=:id and state=:expected",p);
        if(updated==0) throw new IllegalStateException("Revision state changed before transition: "+revisionId+" expected="+expected);
        RuntimeConfigurationRevision r=requireRevision(revisionId);
        audit(r.configSetId(),revisionId,"REVISION_STATE_CHANGED",actor,reason,correlationId,json("from",expected.name(),"to",target.name()));
        return r;
    }

    @Override
    public RuntimeConfigurationRevision publish(String revisionId,String expectedBaseRevisionId,String actor,String reason,String correlationId) {
        RuntimeConfigurationRevision r=requireRevision(revisionId);
        lockConfigSet(r.configSetId());
        r=requireRevision(revisionId);
        if(r.state()!=RuntimeConfigurationRevisionState.APPROVED) throw new IllegalStateException("Only APPROVED revisions may be published: "+revisionId);
        String active=findActiveRevisionId(r.configSetId()).orElse(null);
        String expected=text(expectedBaseRevisionId);
        if(!same(r.baseRevisionId(),expected) || !same(active,expected))
            throw new ConfigurationRevisionConflictException("configSet="+r.configSetId()+" draftBase="+r.baseRevisionId()+" expectedBase="+expected+" currentActive="+active);
        try {
            if(active!=null) {
                int previous=jdbc.update("update runtime_config_revisions set state='SUPERSEDED',updated_by=:actor where revision_id=:id and state='PUBLISHED'",
                    new MapSqlParameterSource("actor",actor).addValue("id",active));
                if(previous!=1) throw new ConfigurationRevisionConflictException("active revision is no longer publishable: "+active);
            }
            int published=jdbc.update("""
                update runtime_config_revisions set state='PUBLISHED',published_at=now(),published_by=:actor,updated_by=:actor
                 where revision_id=:id and state='APPROVED'
                """,new MapSqlParameterSource("actor",required(actor,"actor")).addValue("id",revisionId));
            if(published!=1) throw new ConfigurationRevisionConflictException("revision changed before publication: "+revisionId);
            if(active==null) {
                jdbc.update("""
                    insert into runtime_config_active_revisions(config_set_id,revision_id,activation_version,updated_by)
                    values(:setId,:revisionId,1,:actor)
                    """,new MapSqlParameterSource("setId",r.configSetId()).addValue("revisionId",revisionId).addValue("actor",actor));
            } else {
                int pointer=jdbc.update("""
                    update runtime_config_active_revisions set revision_id=:revisionId,activation_version=activation_version+1,updated_by=:actor
                     where config_set_id=:setId and revision_id=:expectedActive
                    """,new MapSqlParameterSource("revisionId",revisionId).addValue("actor",actor).addValue("setId",r.configSetId()).addValue("expectedActive",active));
                if(pointer!=1) throw new ConfigurationRevisionConflictException("active pointer changed for configSet="+r.configSetId());
            }
        } catch(DataAccessException ex) {
            if(String.valueOf(ex.getMessage()).contains(ConfigurationRevisionConflictException.CODE))
                throw new ConfigurationRevisionConflictException(ex.getMessage());
            throw ex;
        }
        audit(r.configSetId(),revisionId,"REVISION_PUBLISHED",actor,reason,correlationId,json("previousActive",active));
        return requireRevision(revisionId);
    }

    @Override
    public RuntimeConfigurationRevision createRollbackDraft(String newRevisionId,String configSetId,String restoreSourceRevisionId,
            String actor,String reason,String correlationId) {
        lockConfigSet(configSetId);
        String active=findActiveRevisionId(configSetId).orElseThrow(()->new IllegalStateException("Cannot rollback Config Set without an active revision"));
        RuntimeConfigurationRevision source=requireRevision(restoreSourceRevisionId);
        if(!source.configSetId().equals(configSetId)) throw new IllegalArgumentException("Restore source belongs to another Config Set");
        if(source.state()!=RuntimeConfigurationRevisionState.PUBLISHED && source.state()!=RuntimeConfigurationRevisionState.SUPERSEDED)
            throw new IllegalArgumentException("Restore source must be a previously published revision");
        RuntimeConfigurationRevision draft=createDraft(newRevisionId,configSetId,actor,required(reason,"reason"),active,restoreSourceRevisionId,correlationId);
        jdbc.update("""
            insert into runtime_config_revision_items(revision_id,definition_key,value_json,value_fingerprint,created_by,updated_by)
            select :newRevisionId,definition_key,value_json,value_fingerprint,:actor,:actor
              from runtime_config_revision_items where revision_id=:sourceRevisionId
            """,new MapSqlParameterSource("newRevisionId",newRevisionId).addValue("sourceRevisionId",restoreSourceRevisionId).addValue("actor",actor));
        audit(configSetId,newRevisionId,"ROLLBACK_DRAFT_CREATED",actor,reason,correlationId,
            json("rollbackOfRevisionId",active,"restoreSourceRevisionId",restoreSourceRevisionId));
        return draft;
    }

    private RuntimeConfigurationRevision requireRevision(String id){return findRevision(id).orElseThrow(()->new IllegalArgumentException("Revision not found: "+id));}
    private void lockConfigSet(String id){
        List<String> rows=jdbc.query("select config_set_id from runtime_config_sets where config_set_id=:id for update",new MapSqlParameterSource("id",required(id,"configSetId")),(rs,row)->rs.getString(1));
        if(rows.isEmpty()) throw new IllegalArgumentException("Config Set not found: "+id);
    }
    private long nextSequence(String setId){return jdbc.queryForObject("select coalesce(max(sequence_no),0)+1 from runtime_config_revisions where config_set_id=:id",new MapSqlParameterSource("id",setId),Long.class);}
    private void audit(String setId,String revisionId,String action,String actor,String reason,String correlationId,String metadata){
        jdbc.update("""
            insert into runtime_config_audit_logs(audit_id,config_set_id,revision_id,action,actor,reason,correlation_id,metadata_json)
            values(:auditId,:setId,:revisionId,:action,:actor,:reason,:correlationId,cast(:metadata as jsonb))
            """,new MapSqlParameterSource("auditId",UUID.randomUUID().toString()).addValue("setId",setId).addValue("revisionId",revisionId)
              .addValue("action",action).addValue("actor",required(actor,"actor")).addValue("reason",text(reason)).addValue("correlationId",text(correlationId))
              .addValue("metadata",metadata==null?"{}":metadata));
    }
    private static String json(String k,Object v){return "{\""+esc(k)+"\":"+jsonValue(v)+"}";}
    private static String json(String k1,Object v1,String k2,Object v2){return "{\""+esc(k1)+"\":"+jsonValue(v1)+",\""+esc(k2)+"\":"+jsonValue(v2)+"}";}
    private static String jsonValue(Object v){if(v==null)return "null"; if(v instanceof Number||v instanceof Boolean)return String.valueOf(v);return "\""+esc(String.valueOf(v))+"\"";}
    private static String esc(String v){return v.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
    private static boolean same(String a,String b){return a==null?b==null:a.equals(b);}
    private static String text(String v){return v==null||v.isBlank()?null:v.trim();}
    private static String required(String v,String field){if(v==null||v.isBlank())throw new IllegalArgumentException(field+" is required");return v.trim();}

    private static final String REVISION_SELECT="""
      select revision_id,config_set_id,sequence_no,base_revision_id,rollback_of_revision_id,restore_source_revision_id,state,
             definition_schema_version,reason,created_by,created_at,validated_by,validated_at,submitted_by,submitted_at,
             approved_by,approved_at,published_by,published_at,updated_by,updated_at
        from runtime_config_revisions
      """;
    private static final RowMapper<RuntimeConfigurationConfigSet> CONFIG_SET_MAPPER=(rs,row)->new RuntimeConfigurationConfigSet(
        rs.getString("config_set_id"),rs.getString("set_key"),OpenDispatchEnvironment.valueOf(rs.getString("environment")),
        ConfigurationScope.valueOf(rs.getString("scope")),rs.getString("scope_ref"),rs.getString("owner_component"),rs.getString("status"),
        rs.getLong("resource_version"),rs.getObject("created_at",OffsetDateTime.class),rs.getObject("updated_at",OffsetDateTime.class));
    private static final RowMapper<RuntimeConfigurationRevision> REVISION_MAPPER=new RowMapper<>(){
      @Override public RuntimeConfigurationRevision mapRow(ResultSet rs,int row)throws SQLException{return new RuntimeConfigurationRevision(
        rs.getString("revision_id"),rs.getString("config_set_id"),rs.getLong("sequence_no"),rs.getString("base_revision_id"),
        rs.getString("rollback_of_revision_id"),rs.getString("restore_source_revision_id"),RuntimeConfigurationRevisionState.valueOf(rs.getString("state")),
        rs.getInt("definition_schema_version"),rs.getString("reason"),rs.getString("created_by"),rs.getObject("created_at",OffsetDateTime.class),
        rs.getString("validated_by"),rs.getObject("validated_at",OffsetDateTime.class),rs.getString("submitted_by"),rs.getObject("submitted_at",OffsetDateTime.class),
        rs.getString("approved_by"),rs.getObject("approved_at",OffsetDateTime.class),rs.getString("published_by"),rs.getObject("published_at",OffsetDateTime.class),
        rs.getString("updated_by"),rs.getObject("updated_at",OffsetDateTime.class));}};
    private static final RowMapper<RuntimeConfigurationRevisionItem> ITEM_MAPPER=(rs,row)->new RuntimeConfigurationRevisionItem(
        rs.getString("revision_id"),rs.getString("definition_key"),rs.getString("value_json"),rs.getString("value_fingerprint"),
        rs.getString("created_by"),rs.getObject("created_at",OffsetDateTime.class),rs.getString("updated_by"),rs.getObject("updated_at",OffsetDateTime.class));
    private static final RowMapper<RuntimeConfigurationAuditEntry> AUDIT_MAPPER=(rs,row)->new RuntimeConfigurationAuditEntry(
        rs.getString("audit_id"),rs.getString("config_set_id"),rs.getString("revision_id"),rs.getString("action"),rs.getString("actor"),
        rs.getString("reason"),rs.getString("correlation_id"),rs.getString("metadata_json"),rs.getObject("created_at",OffsetDateTime.class));
}
