package com.opensocket.aievent.core.observability;

import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/** Stage 11 observation service. It never chooses a provider, creates an Assignment, starts a Plan or promotes Learning/Fast Path. */
@Service
public class EvidenceEconomicsMemoryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    public EvidenceEconomicsMemoryService(NamedParameterJdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    @Transactional(readOnly=true)
    public List<Map<String,Object>> decisionTrace(String tenantId,String taskId,String runId,String delegationId,int limit){
        String t=tenant(tenantId);bind(t);MapSqlParameterSource p=new MapSqlParameterSource("tenant",t).addValue("limit",bound(limit,1,1000));
        StringBuilder sql=new StringBuilder("select trace_event_id,context_type,context_id,task_id,run_id,delegation_id,stage,event_type,result,capability_code,provider_type,binding_id,decision_id,details_json::text as details_json,occurred_at,source_table,retention_tier,archive_required_before_prune from decision_trace_retention_projection_v53 where tenant_id=:tenant");
        if(!blank(taskId)){sql.append(" and task_id=:task");p.addValue("task",taskId.trim());}
        if(!blank(runId)){sql.append(" and run_id=:run");p.addValue("run",runId.trim());}
        if(!blank(delegationId)){sql.append(" and delegation_id=:delegation");p.addValue("delegation",delegationId.trim());}
        sql.append(" order by occurred_at,trace_event_id limit :limit");return jdbc.queryForList(sql.toString(),p);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> costLedger(String tenantId,String taskId,String contextType,String contextId,int limit){
        String t=tenant(tenantId);bind(t);MapSqlParameterSource p=new MapSqlParameterSource("tenant",t).addValue("limit",bound(limit,1,1000));StringBuilder s=new StringBuilder("select *,evidence_json::text as evidence_text from execution_cost_ledger where tenant_id=:tenant");
        if(!blank(taskId)){s.append(" and task_id=:task");p.addValue("task",taskId.trim());}if(!blank(contextType)){s.append(" and context_type=:ct");p.addValue("ct",contextType.trim().toUpperCase());}if(!blank(contextId)){s.append(" and context_id=:cid");p.addValue("cid",contextId.trim());}s.append(" order by recorded_at desc limit :limit");return jdbc.queryForList(s.toString(),p);
    }

    @Transactional
    public Map<String,Object> appendCost(String tenantId,Map<String,Object> body){
        String t=tenant(tenantId);bind(t);String id=str(body,"costEntryId");if(blank(id))id="cost-"+UUID.randomUUID();String contextType=req(body,"contextType").toUpperCase();String contextId=req(body,"contextId");String entryType=req(body,"entryType").toUpperCase();String sourceRef=req(body,"sourceRef");
        if(!List.of("TASK","DELEGATION","PLAN_STEP","PROVIDER_EXECUTION","ADJUSTMENT").contains(contextType))throw new IllegalArgumentException("Unsupported contextType: "+contextType);
        if(!List.of("ACTUAL","ADJUSTMENT","COMPARISON_ESTIMATE","USAGE_ONLY").contains(entryType))throw new IllegalArgumentException("Unsupported entryType: "+entryType);
        Object actual=body.get("bookedActualCost");if((entryType.equals("ACTUAL")||entryType.equals("ADJUSTMENT"))&&actual==null)throw new IllegalArgumentException(entryType+" requires bookedActualCost");
        String evidence=write(body.getOrDefault("evidence",Map.of()));
        jdbc.update("""
          insert into execution_cost_ledger(tenant_id,cost_entry_id,context_type,context_id,task_id,run_id,step_id,attempt_id,assignment_id,capability_code,operation,provider_type,provider_id,binding_id,entry_type,quantity,unit,token_usage,latency_ms,currency,booked_actual_cost,normalized_comparison_cost,price_profile_ref,source_ref,evidence_json,recorded_at)
          values(:tenant,:id,:ct,:cid,:task,:run,:step,:attempt,:assignment,:capability,:operation,:ptype,:provider,:binding,:etype,:quantity,:unit,:tokens,:latency,:currency,:actual,:normalized,:price,:source,cast(:evidence as jsonb),:at)
          """,params(body).addValue("tenant",t).addValue("id",id).addValue("ct",contextType).addValue("cid",contextId).addValue("etype",entryType).addValue("source",sourceRef).addValue("evidence",evidence).addValue("at",OffsetDateTime.now()));
        return Map.of("costEntryId",id,"status","APPENDED","immutable",true);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> economicsSummary(String tenantId,int limit){String t=tenant(tenantId);bind(t);return jdbc.queryForList("select * from execution_economics_summary_v53 where tenant_id=:tenant order by last_recorded_at desc limit :limit",new MapSqlParameterSource("tenant",t).addValue("limit",bound(limit,1,1000)));}
    @Transactional(readOnly=true)
    public List<Map<String,Object>> costCompleteness(String tenantId,String taskId,int limit){String t=tenant(tenantId);bind(t);MapSqlParameterSource p=new MapSqlParameterSource("tenant",t).addValue("limit",bound(limit,1,1000));String sql="select * from execution_cost_completeness_v53 where tenant_id=:tenant"+(blank(taskId)?"":" and task_id=:task")+" order by started_at desc nulls last limit :limit";if(!blank(taskId))p.addValue("task",taskId.trim());return jdbc.queryForList(sql,p);}

    @Transactional(readOnly=true)
    public Map<String,Object> retentionPolicy(String tenantId){String t=tenant(tenantId);bind(t);List<Map<String,Object>> rows=jdbc.queryForList("select * from evidence_retention_policies where tenant_id=:tenant and status='ACTIVE' order by version desc limit 1",new MapSqlParameterSource("tenant",t));return rows.isEmpty()?Map.of("tenantId",t,"policyId","DEFAULT","hotDays",30,"warmDays",180,"coldDays",2555,"archiveRequiredBeforePrune",true,"source","BUILT_IN_DEFAULT"):rows.getFirst();}

    @Transactional
    public Map<String,Object> upsertRetentionPolicy(String tenantId,Map<String,Object> body){String t=tenant(tenantId);bind(t);String id=blank(str(body,"policyId"))?"default":str(body,"policyId");int hot=intv(body,"hotDays",30),warm=intv(body,"warmDays",180),cold=intv(body,"coldDays",2555);if(hot<1||warm<=hot||cold<warm)throw new IllegalArgumentException("Retention must satisfy 1 <= hotDays < warmDays <= coldDays");boolean archive=bool(body,"archiveRequiredBeforePrune",true);int old=jdbc.queryForObject("select coalesce(max(version),0) from evidence_retention_policies where tenant_id=:tenant and policy_id=:id",new MapSqlParameterSource("tenant",t).addValue("id",id),Integer.class);jdbc.update("update evidence_retention_policies set status='RETIRED',updated_at=now() where tenant_id=:tenant and status='ACTIVE'",new MapSqlParameterSource("tenant",t));int version=old+1;jdbc.update("insert into evidence_retention_policies(tenant_id,policy_id,hot_days,warm_days,cold_days,archive_required_before_prune,status,version,updated_by) values(:tenant,:id,:hot,:warm,:cold,:archive,'ACTIVE',:version,'STAGE11_ADMIN') on conflict(tenant_id,policy_id) do update set hot_days=excluded.hot_days,warm_days=excluded.warm_days,cold_days=excluded.cold_days,archive_required_before_prune=excluded.archive_required_before_prune,status='ACTIVE',version=excluded.version,updated_by=excluded.updated_by,updated_at=now()",new MapSqlParameterSource("tenant",t).addValue("id",id).addValue("hot",hot).addValue("warm",warm).addValue("cold",cold).addValue("archive",archive).addValue("version",version));return Map.of("policyId",id,"version",version,"status","ACTIVE","hotDays",hot,"warmDays",warm,"coldDays",cold,"archiveRequiredBeforePrune",archive);}

    @Transactional(readOnly=true)
    public List<Map<String,Object>> archiveSegments(String tenantId,int limit){String t=tenant(tenantId);bind(t);return jdbc.queryForList("select * from evidence_archive_segments where tenant_id=:tenant order by range_start desc,created_at desc limit :limit",new MapSqlParameterSource("tenant",t).addValue("limit",bound(limit,1,1000)));}

    @Transactional
    public Map<String,Object> registerArchiveSegment(String tenantId,Map<String,Object> body){String t=tenant(tenantId);bind(t);String id=blank(str(body,"archiveSegmentId"))?"archive-"+UUID.randomUUID():str(body,"archiveSegmentId");String source=req(body,"sourceFamily");String start=req(body,"rangeStart"),end=req(body,"rangeEnd");jdbc.update("insert into evidence_archive_segments(tenant_id,archive_segment_id,source_family,range_start,range_end,object_ref,content_digest,record_count,status) values(:tenant,:id,:source,cast(:start as timestamptz),cast(:end as timestamptz),:object,:digest,:count,'PLANNED')",new MapSqlParameterSource("tenant",t).addValue("id",id).addValue("source",source).addValue("start",start).addValue("end",end).addValue("object",obj(body,"objectRef")).addValue("digest",obj(body,"contentDigest")).addValue("count",obj(body,"recordCount")));return Map.of("archiveSegmentId",id,"status","PLANNED","pruneAuthorized",false);}

    @Transactional
    public Map<String,Object> verifyArchiveSegment(String tenantId,String segmentId,Map<String,Object> body){String t=tenant(tenantId);bind(t);String object=req(body,"objectRef"),digest=req(body,"contentDigest");int n=jdbc.update("update evidence_archive_segments set object_ref=:object,content_digest=:digest,record_count=coalesce(:count,record_count),status='VERIFIED',verified_at=now() where tenant_id=:tenant and archive_segment_id=:id and status in ('PLANNED','EXPORTED')",new MapSqlParameterSource("tenant",t).addValue("id",segmentId).addValue("object",object).addValue("digest",digest).addValue("count",obj(body,"recordCount")));if(n!=1)throw new IllegalArgumentException("Archive segment not found or not verifiable: "+segmentId);return Map.of("archiveSegmentId",segmentId,"status","VERIFIED","pruneAuthorized",false,"note","Stage11 never performs destructive prune; VERIFIED is only prerequisite evidence for a later retention worker.");}

    @Transactional
    public Map<String,Object> refreshExecutionMemory(String tenantId,int days){String t=tenant(tenantId);bind(t);int d=bound(days,1,3650);LocalDate end=LocalDate.now(),start=end.minusDays(d-1L);MapSqlParameterSource p=new MapSqlParameterSource("tenant",t).addValue("start",start).addValue("end",end);
        jdbc.update("delete from execution_memory_daily_buckets where tenant_id=:tenant and bucket_date between :start and :end",p);
        int buckets=jdbc.update("""
          insert into execution_memory_daily_buckets(tenant_id,bucket_date,capability_code,operation,provider_type,provider_id,binding_id,sample_count,success_count,failure_count,pending_count,latency_sample_count,latency_sum_ms,max_latency_ms,booked_actual_cost,normalized_comparison_cost,unbooked_count,refreshed_at)
          select o.tenant_id,o.started_at::date,o.capability_code,o.operation,o.provider_type,o.provider_id,o.binding_id,
                 count(*),
                 count(*) filter(where o.outcome in ('RESULT_SUCCEEDED','SUCCEEDED')),
                 count(*) filter(where o.outcome in ('RESULT_FAILED','FAILED','DEAD_LETTER','TIMED_OUT','CANCELLED')),
                 count(*) filter(where o.outcome not in ('RESULT_SUCCEEDED','SUCCEEDED','RESULT_FAILED','FAILED','DEAD_LETTER','TIMED_OUT','CANCELLED')),
                 count(o.latency_ms),coalesce(sum(o.latency_ms),0),max(o.latency_ms),
                 coalesce(sum(c.booked_actual_cost),0),coalesce(sum(c.normalized_comparison_cost),0),
                 count(*) filter(where c.cost_completeness='UNBOOKED'),now()
            from execution_observation_projection_v53 o
            left join execution_cost_completeness_v53 c on c.tenant_id=o.tenant_id and c.context_type=o.context_type and c.context_id=o.context_id
           where o.tenant_id=:tenant and o.started_at::date between :start and :end
           group by o.tenant_id,o.started_at::date,o.capability_code,o.operation,o.provider_type,o.provider_id,o.binding_id
          """,p);
        Long observations=jdbc.queryForObject("select count(*) from execution_observation_projection_v53 where tenant_id=:tenant and started_at::date between :start and :end",p,Long.class);String refresh="memory-refresh-"+UUID.randomUUID();jdbc.update("insert into execution_memory_refresh_runs(tenant_id,refresh_id,window_start,window_end,bucket_count,observation_count,status,evidence_json) values(:tenant,:id,:start,:end,:buckets,:observations,'COMPLETED',cast(:evidence as jsonb))",p.addValue("id",refresh).addValue("buckets",buckets).addValue("observations",observations==null?0:observations).addValue("evidence",write(Map.of("authority","OBSERVATION_ONLY","learningPromotion",false,"fastPathPromotion",false))));return Map.of("refreshId",refresh,"status","COMPLETED","windowStart",start,"windowEnd",end,"bucketCount",buckets,"observationCount",observations==null?0:observations,"routingAuthority",false);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> executionMemory(String tenantId,String capabilityCode,String providerType,int limit){String t=tenant(tenantId);bind(t);MapSqlParameterSource p=new MapSqlParameterSource("tenant",t).addValue("limit",bound(limit,1,1000));StringBuilder s=new StringBuilder("select * from execution_memory_rollup_v53 where tenant_id=:tenant");if(!blank(capabilityCode)){s.append(" and capability_code=:cap");p.addValue("cap",capabilityCode.trim());}if(!blank(providerType)){s.append(" and provider_type=:pt");p.addValue("pt",providerType.trim().toUpperCase());}s.append(" order by sample_count desc,last_bucket_date desc limit :limit");return jdbc.queryForList(s.toString(),p);}

    private MapSqlParameterSource params(Map<String,Object>b){return new MapSqlParameterSource().addValue("task",obj(b,"taskId")).addValue("run",obj(b,"runId")).addValue("step",obj(b,"stepId")).addValue("attempt",obj(b,"attemptId")).addValue("assignment",obj(b,"assignmentId")).addValue("capability",obj(b,"capabilityCode")).addValue("operation",obj(b,"operation")).addValue("ptype",obj(b,"providerType")).addValue("provider",obj(b,"providerId")).addValue("binding",obj(b,"bindingId")).addValue("quantity",obj(b,"quantity")).addValue("unit",obj(b,"unit")).addValue("tokens",obj(b,"tokenUsage")).addValue("latency",obj(b,"latencyMs")).addValue("currency",obj(b,"currency")).addValue("actual",obj(b,"bookedActualCost")).addValue("normalized",obj(b,"normalizedComparisonCost")).addValue("price",obj(b,"priceProfileRef"));}
    private String tenant(String v){if(blank(v))throw new IllegalArgumentException("tenantId is required");return v.trim();}
    private void bind(String t){bindTenantContext(t,"STAGE11_OBSERVABILITY");}
    private void bindTenantContext(String tenantId,String backgroundActor){
        IamTenantExecutionContext current=IamTenantContextHolder.current().orElse(null);
        String actor=backgroundActor;
        if(current!=null){
            if(!tenantId.equals(current.tenantId()))throw new IllegalStateException("TENANT_CONTEXT_MISMATCH");
            actor=current.actorId();
        }else{
            if(!TransactionSynchronizationManager.isActualTransactionActive()||!TransactionSynchronizationManager.isSynchronizationActive())throw new IllegalStateException("TENANT_TRANSACTION_REQUIRED");
            IamTenantContextHolder.Scope scope=IamTenantContextHolder.open(new IamTenantExecutionContext(tenantId,backgroundActor));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){scope.close();}});
        }
        MapSqlParameterSource context=new MapSqlParameterSource("tenant",tenantId).addValue("actor",actor);
        jdbc.queryForObject("select set_config('app.current_tenant_id',:tenant,true) || ':' || set_config('app.current_actor_id',:actor,true)",context,String.class);
    }
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception ex){throw new IllegalArgumentException("JSON serialization failed",ex);}}private static Object obj(Map<String,Object>b,String k){return b==null?null:b.get(k);}private static String str(Map<String,Object>b,String k){Object v=obj(b,k);return v==null?null:String.valueOf(v);}private static String req(Map<String,Object>b,String k){String v=str(b,k);if(blank(v))throw new IllegalArgumentException(k+" is required");return v.trim();}private static boolean blank(String v){return v==null||v.isBlank();}private static int bound(int v,int min,int max){return Math.max(min,Math.min(max,v));}private static int intv(Map<String,Object>b,String k,int d){Object v=obj(b,k);return v instanceof Number n?n.intValue():v==null?d:Integer.parseInt(String.valueOf(v));}private static boolean bool(Map<String,Object>b,String k,boolean d){Object v=obj(b,k);return v==null?d:Boolean.parseBoolean(String.valueOf(v));}
}
