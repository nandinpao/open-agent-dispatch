package com.opensocket.aievent.core.workload;

import com.opensocket.aievent.core.event.EventIntakeRequest;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.iam.runtime.eventintake.EventIntakeAuthorizationEvidence;
import com.opensocket.aievent.core.iam.runtime.machine.TrustedClientIpResolver;
import com.opensocket.aievent.core.iam.security.contract.MachineAuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds server-authoritative, immutable Event Intake provenance; caller-supplied organization data is never trusted. */
@Service
public class EventIntakeWorkloadContextFactory {
    private final NamedParameterJdbcTemplate jdbc;
    private final TrustedClientIpResolver clientIp;
    private final Clock clock;

    public EventIntakeWorkloadContextFactory(NamedParameterJdbcTemplate jdbc, TrustedClientIpResolver clientIp, Clock clock) {
        this.jdbc=jdbc; this.clientIp=clientIp; this.clock=clock;
    }

    @Transactional(readOnly = true)
    public WorkloadContext capture(EventIntakeRequest body, HttpServletRequest request, EventIntakeAuthorizationEvidence evidence) {
        EventIntakeAuthorizationEvidence auth=evidence==null?EventIntakeAuthorizationEvidence.unenforced():evidence;
        MachineAuthenticationContext machine=auth.machineContext();
        String tenant=machine==null?required(body.getTenantId(),"UNKNOWN"):machine.principal().activeTenant().tenantId();
        OpenDispatchRequestContext http=OpenDispatchRequestContextHolder.current().orElse(null);
        bindDatabaseTenantContext(tenant, http, machine);
        SourceOwnership ownership=sourceOwnership(tenant, body.getSourceSystem());
        String correlation=first(http==null?null:http.correlationId(),header(request,"X-Correlation-Id"),body.getCorrelationId());
        String requestId=first(http==null?null:http.requestId(),header(request,"X-Request-Id"));
        SecurityEpoch epoch=machine==null?SecurityEpoch.ZERO:machine.securityEpoch();
        String principalType=machine==null?"INTEGRATION":machine.principal().principalType().name();
        String principalId=machine==null?"legacy:"+required(body.getSourceSystem(),"UNKNOWN"):machine.principal().principalId();
        String credentialId=machine==null?"":machine.credential().credentialId();
        String clientId=machine==null?"":machine.credential().clientId();
        return new WorkloadContext(tenant,principalType,principalId,principalType,principalId,credentialId,clientId,
                ownership.departmentId(),ownership.groupId(),"SOURCE_SYSTEM",ownership.status(),body.getSourceSystem(),
                request==null?"/api/events/intake":request.getRequestURI(),clientIp.resolve(request),auth.decisionId(),
                epoch.globalEpoch(),epoch.tenantEpoch(),epoch.principalEpoch(),requestId,correlation,traceId(request),
                auth.authenticationMethod(),"PRODUCTION",false,clock.instant());
    }

    /**
     * Event Intake ownership lookup uses NamedParameterJdbcTemplate and therefore does not pass
     * through TenantTransactionMybatisInterceptor. Bind PostgreSQL's transaction-local tenant
     * context explicitly before reading tenant-RLS protected Source System / Group tables.
     *
     * <p>The tenant is server-authoritative: JWT mode uses the authenticated machine tenant;
     * legacy mode uses the already-accepted intake tenant. The actor is diagnostic/audit context
     * only and never grants authorization.</p>
     */
    private void bindDatabaseTenantContext(String tenant, OpenDispatchRequestContext http, MachineAuthenticationContext machine) {
        String normalizedTenant=required(tenant,"UNKNOWN");
        String actor=machine==null
                ?first(http==null?null:http.operatorId(),"core-internal-event_ingestion")
                :first(machine.principal().principalId(),http==null?null:http.operatorId(),"event-intake-machine");
        jdbc.getJdbcTemplate().queryForObject(
                "select set_config('app.current_tenant_id', ?, true)",String.class,normalizedTenant);
        jdbc.getJdbcTemplate().queryForObject(
                "select set_config('app.current_actor_id', ?, true)",String.class,actor);
    }

    private SourceOwnership sourceOwnership(String tenant,String sourceSystem){
        try{
            Map<String,Object> row=jdbc.queryForMap("""
                    select coalesce(s.owner_department_id,g.owner_department_id) as owner_department_id,
                           s.owner_group_id,s.status
                      from source_systems s
                      left join organization_groups g
                        on g.tenant_id=s.tenant_id and g.group_id=s.owner_group_id and g.status='ACTIVE'
                     where s.tenant_id=:tenantId and s.source_system_id=:sourceSystem
                    """,new MapSqlParameterSource().addValue("tenantId",tenant).addValue("sourceSystem",sourceSystem));
            String department=text(row.get("owner_department_id")); String group=text(row.get("owner_group_id"));
            // An ACTIVE Source System with neither Department nor Group is intentionally Tenant-owned,
            // which is still an authoritative organizational attribution rather than an unresolved lookup.
            String status="ACTIVE".equalsIgnoreCase(text(row.get("status")))?"RESOLVED":"UNRESOLVED";
            return new SourceOwnership(first(department,"UNASSIGNED"),group,status);
        }catch(EmptyResultDataAccessException ex){return new SourceOwnership("UNASSIGNED",null,"UNRESOLVED");}
    }
    private static String traceId(HttpServletRequest r){
        String traceparent=header(r,"traceparent"); if(traceparent==null)return "";
        String[] p=traceparent.split("-"); return p.length>=4&&p[1].matches("[0-9a-fA-F]{32}")&&!p[1].matches("0{32}")?p[1].toLowerCase():"";
    }
    private static String header(HttpServletRequest r,String name){return r==null?null:r.getHeader(name);}
    private static String text(Object v){return v==null?null:String.valueOf(v);}
    private static String required(String v,String fallback){return v==null||v.isBlank()?fallback:v.trim();}
    private static String first(String... values){if(values!=null)for(String v:values)if(v!=null&&!v.isBlank())return v.trim();return "";}
    private record SourceOwnership(String departmentId,String groupId,String status){}
}
