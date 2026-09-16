package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stage 5 safety semantics reused by Stage 6/7/8 before every external network action. */
@Service
public class CapabilityRemoteExecutionSafetyService {
    private final NamedParameterJdbcTemplate jdbc;
    public CapabilityRemoteExecutionSafetyService(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional(readOnly=true)
    public Decision evaluate(String tenantId,String assignmentId){
        bind(tenantId);
        try{return jdbc.queryForObject("""
          select a.assignment_id,a.binding_id,a.execution_safety_mode,a.lease_id,a.fencing_token,a.lease_expires_at,a.status,
                 e.status envelope_status,e.binding_id envelope_binding,e.valid_until,e.authorization_epoch,e.revocation_version
            from task_assignments a
            left join capability_runtime_authorization_envelopes e on e.tenant_id=a.tenant_id and e.assignment_id=a.assignment_id
           where a.tenant_id=:tenant and a.assignment_id=:assignment
          """,new MapSqlParameterSource("tenant",tenantId).addValue("assignment",assignmentId),(rs,n)->{
            OffsetDateTime now=OffsetDateTime.now();
            if(!"ASSIGNED".equals(rs.getString("status")))return Decision.deny("ASSIGNMENT_NOT_ACTIVE");
            if(!"REMOTE_NATIVE_IDEMPOTENT".equals(rs.getString("execution_safety_mode")))return Decision.deny("REMOTE_IDEMPOTENT_SAFETY_MODE_REQUIRED");
            if(blank(rs.getString("lease_id"))||blank(rs.getString("fencing_token"))||rs.getObject("lease_expires_at")==null)return Decision.deny("REMOTE_FENCE_INCOMPLETE");
            OffsetDateTime lease=rs.getObject("lease_expires_at",OffsetDateTime.class);if(!lease.isAfter(now))return Decision.deny("ASSIGNMENT_LEASE_EXPIRED");
            if(!"ACTIVE".equals(rs.getString("envelope_status")))return Decision.deny("AUTHORIZATION_ENVELOPE_NOT_ACTIVE");
            if(!eq(rs.getString("binding_id"),rs.getString("envelope_binding")))return Decision.deny("AUTHORIZATION_BINDING_MISMATCH");
            OffsetDateTime valid=rs.getObject("valid_until",OffsetDateTime.class);if(valid!=null&&!valid.isAfter(now))return Decision.deny("AUTHORIZATION_ENVELOPE_EXPIRED");
            return Decision.allow(rs.getLong("authorization_epoch"),rs.getLong("revocation_version"));
          });}catch(EmptyResultDataAccessException ex){return Decision.deny("ASSIGNMENT_NOT_FOUND");}
    }
    public void requireAllowed(String tenant,String assignment){Decision d=evaluate(tenant,assignment);if(!d.allowed())throw new IllegalStateException(d.reasonCode());}
    private void bind(String tenant){jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_tenant_id', ?, true)",String.class,tenant);jdbc.getJdbcTemplate().queryForObject("select set_config('app.current_actor_id', ?, true)",String.class,"remote-execution-safety");}
    private static boolean blank(String v){return v==null||v.isBlank();}private static boolean eq(String a,String b){return a==null?b==null:a.equals(b);}
    public record Decision(boolean allowed,String reasonCode,Long authorizationEpoch,Long revocationVersion){static Decision allow(long e,long r){return new Decision(true,"ALLOW",e,r);}static Decision deny(String r){return new Decision(false,r,null,null);}}
}
