package com.opensocket.aievent.core.capability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C0-B6 peer error mapping authority.
 *
 * <p>The remote peer may contribute an exact error-code mapping, but it never chooses the runtime
 * disposition directly. OpenDispatch first computes a baseline class and then derives a canonical
 * disposition. Protected AUTHENTICATION/AUTHORIZATION/TRUST/RESIDENCY/SECURITY baselines cannot be
 * weakened by an override.</p>
 */
@Service
public class A2APeerErrorMappingService {
    private static final List<String> OPERATIONS=List.of("ANY","SEND_MESSAGE","GET_TASK","SUBSCRIBE","PUSH_CONFIG","CANCEL","TASK_TERMINAL");
    private static final List<String> CLASSES=List.of("AUTHENTICATION","AUTHORIZATION","TRUST","RESIDENCY","SECURITY","THROTTLED","TEMPORARY","UNAVAILABLE","NOT_FOUND","CONFLICT","INVALID_REQUEST","PROTOCOL","REMOTE_FAILURE","UNKNOWN");
    private final NamedParameterJdbcTemplate jdbc;

    public A2APeerErrorMappingService(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional(readOnly=true)
    public List<Map<String,Object>> overrides(String tenant,String peerId){
        bind(tenant,"a2a-peer-error-mapping-read");requirePeer(tenant,peerId);
        return jdbc.queryForList("""
          select override_id,peer_id,interface_id,operation_scope,remote_error_code,http_status,canonical_error_class,canonical_error_code,reason,override_status,activated_at,retired_at,created_at,updated_at
            from a2a_peer_error_mapping_overrides
           where tenant_id=:tenant and peer_id=:peer
           order by case override_status when 'ACTIVE' then 0 when 'DRAFT' then 1 else 2 end,updated_at desc,override_id
          """,new MapSqlParameterSource("tenant",tenant).addValue("peer",required(peerId,"peerId")));
    }

    @Transactional(readOnly=true)
    public Map<String,Object> override(String tenant,String peerId,String overrideId){
        bind(tenant,"a2a-peer-error-mapping-read");return requireOverride(tenant,peerId,overrideId);
    }

    @Transactional
    public Map<String,Object> upsertDraft(String tenant,String peerId,String overrideId,String interfaceId,String operationScope,String remoteErrorCode,Integer httpStatus,String canonicalErrorClass,String canonicalErrorCode,String reason){
        bind(tenant,"a2a-peer-error-mapping-write");String peer=required(peerId,"peerId"),id=required(overrideId,"overrideId"),iface=blankToNull(interfaceId),op=operation(operationScope),remote=remoteCode(remoteErrorCode),target=errorClass(canonicalErrorClass),code=canonicalCode(canonicalErrorCode);
        requirePeer(tenant,peer);if(iface!=null)requireInterface(tenant,peer,iface);if(httpStatus!=null&&(httpStatus<100||httpStatus>599))throw new IllegalArgumentException("A2A_PEER_ERROR_HTTP_STATUS_INVALID");
        String baseline=baseline(tenant,httpStatus,remote);if(protectedClass(tenant,baseline)&&!baseline.equals(target))throw new IllegalArgumentException("A2A_PROTECTED_ERROR_CLASS_REMAP_FORBIDDEN:"+baseline+"->"+target);
        int n=jdbc.update("""
          insert into a2a_peer_error_mapping_overrides(tenant_id,override_id,peer_id,interface_id,operation_scope,remote_error_code,http_status,canonical_error_class,canonical_error_code,reason,override_status,created_by,created_at,updated_at)
          values(:tenant,:id,:peer,:interface,:operation,:remote,:http,:class,:code,:reason,'DRAFT','a2a-peer-error-mapping-admin',now(),now())
          on conflict(tenant_id,override_id) do update set peer_id=excluded.peer_id,interface_id=excluded.interface_id,operation_scope=excluded.operation_scope,remote_error_code=excluded.remote_error_code,http_status=excluded.http_status,canonical_error_class=excluded.canonical_error_class,canonical_error_code=excluded.canonical_error_code,reason=excluded.reason,updated_at=now()
          where a2a_peer_error_mapping_overrides.override_status='DRAFT'
          """,new MapSqlParameterSource("tenant",tenant).addValue("id",id).addValue("peer",peer).addValue("interface",iface).addValue("operation",op).addValue("remote",remote).addValue("http",httpStatus).addValue("class",target).addValue("code",code).addValue("reason",requiredValue(reason,"reason")));
        if(n!=1)throw new IllegalArgumentException("A2A_PEER_ERROR_MAPPING_NOT_DRAFT");return requireOverride(tenant,peer,id);
    }

    @Transactional
    public Map<String,Object> activate(String tenant,String peerId,String overrideId){
        bind(tenant,"a2a-peer-error-mapping-activate");Map<String,Object> row=requireOverride(tenant,peerId,overrideId);if(!"DRAFT".equals(String.valueOf(row.get("override_status"))))throw new IllegalArgumentException("A2A_PEER_ERROR_MAPPING_NOT_DRAFT");
        String baseline=baseline(tenant,intValue(row.get("http_status")),String.valueOf(row.get("remote_error_code"))),target=String.valueOf(row.get("canonical_error_class"));if(protectedClass(tenant,baseline)&&!baseline.equals(target))throw new IllegalArgumentException("A2A_PROTECTED_ERROR_CLASS_REMAP_FORBIDDEN:"+baseline+"->"+target);
        MapSqlParameterSource p=new MapSqlParameterSource("tenant",tenant).addValue("peer",peerId).addValue("id",overrideId).addValue("interface",row.get("interface_id")).addValue("operation",row.get("operation_scope")).addValue("remote",row.get("remote_error_code")).addValue("http",row.get("http_status"));
        jdbc.update("""
          update a2a_peer_error_mapping_overrides set override_status='RETIRED',retired_at=now(),updated_at=now()
           where tenant_id=:tenant and peer_id=:peer and override_id<>:id and override_status='ACTIVE'
             and interface_id is not distinct from :interface and operation_scope=:operation and remote_error_code=:remote and http_status is not distinct from :http
          """,p);
        int n=jdbc.update("update a2a_peer_error_mapping_overrides set override_status='ACTIVE',activated_at=now(),retired_at=null,updated_at=now() where tenant_id=:tenant and peer_id=:peer and override_id=:id and override_status='DRAFT'",p);if(n!=1)throw new IllegalArgumentException("A2A_PEER_ERROR_MAPPING_ACTIVATION_FAILED");return requireOverride(tenant,peerId,overrideId);
    }

    @Transactional
    public Map<String,Object> retire(String tenant,String peerId,String overrideId){
        bind(tenant,"a2a-peer-error-mapping-retire");int n=jdbc.update("update a2a_peer_error_mapping_overrides set override_status='RETIRED',retired_at=now(),updated_at=now() where tenant_id=:tenant and peer_id=:peer and override_id=:id and override_status in ('DRAFT','ACTIVE')",new MapSqlParameterSource("tenant",tenant).addValue("peer",required(peerId,"peerId")).addValue("id",required(overrideId,"overrideId")));if(n!=1)throw new IllegalArgumentException("A2A_PEER_ERROR_MAPPING_NOT_RETIRABLE");return requireOverride(tenant,peerId,overrideId);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> explain(String tenant,String peerId,String interfaceId,String operation,Integer httpStatus,String remoteErrorCode){
        bind(tenant,"a2a-peer-error-mapping-explain");requirePeer(tenant,peerId);String iface=blankToNull(interfaceId);if(iface!=null)requireInterface(tenant,peerId,iface);Resolution r=resolveInternal(tenant,peerId,iface,operation(operation),httpStatus,blankToNull(remoteErrorCode)==null?null:remoteCode(remoteErrorCode),null);
        return resolutionMap(r);
    }

    @Transactional
    public Resolution resolveAndRecord(String tenant,String executionId,String trackingId,String peerId,String interfaceId,String operation,Integer httpStatus,Map<String,Object> body,String fallbackMessage){
        bind(tenant,"a2a-peer-error-runtime");String remote=remoteErrorCode(body),message=remoteErrorMessage(body,fallbackMessage);Resolution r=resolveInternal(tenant,peerId,interfaceId,operation(operation),httpStatus,remote,message);String evidenceId="a2a-error-evidence-"+UUID.randomUUID();
        jdbc.update("""
          insert into a2a_peer_error_resolution_evidence(tenant_id,evidence_id,execution_id,tracking_id,peer_id,interface_id,operation_scope,http_status,remote_error_code,remote_error_message,baseline_error_class,resolved_error_class,canonical_error_code,error_disposition,mapping_source,override_id,protected_baseline,observed_at)
          values(:tenant,:id,:execution,:tracking,:peer,:interface,:operation,:http,:remote,:message,:baseline,:resolved,:code,:disposition,:source,:override,:protected,now())
          """,new MapSqlParameterSource("tenant",tenant).addValue("id",evidenceId).addValue("execution",blankToNull(executionId)).addValue("tracking",blankToNull(trackingId)).addValue("peer",required(peerId,"peerId")).addValue("interface",required(interfaceId,"interfaceId")).addValue("operation",r.operation()).addValue("http",httpStatus).addValue("remote",r.remoteErrorCode()).addValue("message",r.remoteErrorMessage()).addValue("baseline",r.baselineErrorClass()).addValue("resolved",r.resolvedErrorClass()).addValue("code",r.canonicalErrorCode()).addValue("disposition",r.disposition()).addValue("source",r.mappingSource()).addValue("override",r.overrideId()).addValue("protected",r.protectedBaseline()));
        return new Resolution(r.operation(),r.httpStatus(),r.remoteErrorCode(),r.remoteErrorMessage(),r.baselineErrorClass(),r.resolvedErrorClass(),r.canonicalErrorCode(),r.disposition(),r.mappingSource(),r.overrideId(),r.protectedBaseline(),evidenceId);
    }

    private Resolution resolveInternal(String tenant,String peerId,String interfaceId,String operation,Integer httpStatus,String remoteErrorCode,String message){
        String remote=blankToNull(remoteErrorCode);MapSqlParameterSource p=new MapSqlParameterSource("tenant",tenant).addValue("peer",required(peerId,"peerId")).addValue("interface",blankToNull(interfaceId)).addValue("operation",operation).addValue("http",httpStatus).addValue("remote",remote);
        return jdbc.queryForObject("select baseline_error_class,resolved_error_class,canonical_error_code,error_disposition,mapping_source,override_id,protected_baseline from a2a_peer_error_resolve(:tenant,:peer,:interface,:operation,:http,:remote)",p,(rs,n)->new Resolution(operation,httpStatus,remote,message,rs.getString("baseline_error_class"),rs.getString("resolved_error_class"),rs.getString("canonical_error_code"),rs.getString("error_disposition"),rs.getString("mapping_source"),rs.getString("override_id"),rs.getBoolean("protected_baseline"),null));
    }

    @SuppressWarnings("unchecked")
    static String remoteErrorCode(Map<String,Object> body){
        if(body==null)return null;Object err=body.get("error");Map<String,Object> nested=err instanceof Map<?,?> m?(Map<String,Object>)(Map<?,?>)m:Map.of();Object value=first(nested.get("code"),nested.get("errorCode"),nested.get("error_code"),body.get("errorCode"),body.get("error_code"),body.get("code"));return value==null?null:remoteCode(String.valueOf(value));
    }
    @SuppressWarnings("unchecked")
    static String remoteErrorMessage(Map<String,Object> body,String fallback){
        if(body==null)return blankToNull(fallback);Object err=body.get("error");Map<String,Object> nested=err instanceof Map<?,?> m?(Map<String,Object>)(Map<?,?>)m:Map.of();Object value=first(nested.get("message"),body.get("errorMessage"),body.get("error_message"),body.get("message"));return value==null?blankToNull(fallback):trim(String.valueOf(value),2000);
    }

    private Map<String,Object> requireOverride(String tenant,String peer,String id){try{return jdbc.queryForMap("select override_id,peer_id,interface_id,operation_scope,remote_error_code,http_status,canonical_error_class,canonical_error_code,reason,override_status,activated_at,retired_at,created_at,updated_at from a2a_peer_error_mapping_overrides where tenant_id=:tenant and peer_id=:peer and override_id=:id",new MapSqlParameterSource("tenant",tenant).addValue("peer",required(peer,"peerId")).addValue("id",required(id,"overrideId")));}catch(EmptyResultDataAccessException ex){throw new IllegalArgumentException("A2A_PEER_ERROR_MAPPING_NOT_FOUND");}}
    private void requirePeer(String tenant,String peer){Integer n=jdbc.queryForObject("select count(*) from a2a_peer_registrations where tenant_id=:tenant and peer_id=:peer",new MapSqlParameterSource("tenant",tenant).addValue("peer",required(peer,"peerId")),Integer.class);if(n==null||n!=1)throw new IllegalArgumentException("A2A_PEER_NOT_FOUND");}
    private void requireInterface(String tenant,String peer,String iface){Integer n=jdbc.queryForObject("select count(*) from a2a_peer_interfaces where tenant_id=:tenant and peer_id=:peer and interface_id=:interface",new MapSqlParameterSource("tenant",tenant).addValue("peer",peer).addValue("interface",iface),Integer.class);if(n==null||n!=1)throw new IllegalArgumentException("A2A_PEER_INTERFACE_NOT_FOUND");}
    private String baseline(String tenant,Integer http,String remote){return jdbc.queryForObject("select a2a_peer_error_baseline_class(:http,:remote)",new MapSqlParameterSource("http",http).addValue("remote",remote),String.class);}
    private boolean protectedClass(String tenant,String errorClass){Boolean v=jdbc.queryForObject("select a2a_peer_error_protected_class(:class)",new MapSqlParameterSource("class",errorClass),Boolean.class);return Boolean.TRUE.equals(v);}
    private void bind(String tenant,String actor){jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,required(tenant,"tenant"));jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,actor);}
    private static String operation(String v){String x=v==null||v.isBlank()?"ANY":v.trim().toUpperCase();if(!OPERATIONS.contains(x))throw new IllegalArgumentException("A2A_PEER_ERROR_OPERATION_INVALID");return x;}
    private static String errorClass(String v){String x=requiredValue(v,"canonicalErrorClass").toUpperCase();if(!CLASSES.contains(x))throw new IllegalArgumentException("A2A_PEER_ERROR_CLASS_INVALID");return x;}
    private static String canonicalCode(String v){String x=requiredValue(v,"canonicalErrorCode").toUpperCase();if(!x.matches("[A-Z0-9_]{3,180}"))throw new IllegalArgumentException("A2A_PEER_CANONICAL_ERROR_CODE_INVALID");return x;}
    private static String remoteCode(String v){String x=requiredValue(v,"remoteErrorCode").trim().toUpperCase();if(x.length()>180)x=x.substring(0,180);return x;}
    private static String required(String v,String name){return requiredValue(v,name);}
    private static String requiredValue(String v,String name){if(v==null||v.isBlank())throw new IllegalArgumentException(name+" is required");return v.trim();}
    private static String blankToNull(String v){return v==null||v.isBlank()?null:v.trim();}
    private static Object first(Object... values){for(Object v:values)if(v!=null&&!String.valueOf(v).isBlank())return v;return null;}
    private static Integer intValue(Object v){if(v==null)return null;if(v instanceof Number n)return n.intValue();return Integer.valueOf(String.valueOf(v));}
    private static String trim(String v,int max){if(v==null)return null;String x=v.trim();return x.length()<=max?x:x.substring(0,max);}
    private static Map<String,Object> resolutionMap(Resolution r){Map<String,Object> out=new LinkedHashMap<>();out.put("operation",r.operation());out.put("httpStatus",r.httpStatus());out.put("remoteErrorCode",r.remoteErrorCode());out.put("remoteErrorMessage",r.remoteErrorMessage());out.put("baselineErrorClass",r.baselineErrorClass());out.put("resolvedErrorClass",r.resolvedErrorClass());out.put("canonicalErrorCode",r.canonicalErrorCode());out.put("disposition",r.disposition());out.put("mappingSource",r.mappingSource());out.put("overrideId",r.overrideId());out.put("protectedBaseline",r.protectedBaseline());return out;}

    public record Resolution(String operation,Integer httpStatus,String remoteErrorCode,String remoteErrorMessage,String baselineErrorClass,String resolvedErrorClass,String canonicalErrorCode,String disposition,String mappingSource,String overrideId,boolean protectedBaseline,String evidenceId){
        public boolean retryable(){return "RETRY".equals(disposition)||"RECONCILE".equals(disposition);}
        public boolean blocking(){return "BLOCK".equals(disposition);}
        public boolean terminal(){return "TERMINAL".equals(disposition);}
    }
}
