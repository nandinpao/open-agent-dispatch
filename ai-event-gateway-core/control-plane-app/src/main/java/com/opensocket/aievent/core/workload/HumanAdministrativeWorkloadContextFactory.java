package com.opensocket.aievent.core.workload;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.iam.runtime.security.IamRuntimeAuthenticationToken;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;

/** Captures immutable server-authoritative Human workload provenance for administrative Task creation. */
@Service
public class HumanAdministrativeWorkloadContextFactory {
    private final JdbcTemplate jdbc; private final Clock clock;
    public HumanAdministrativeWorkloadContextFactory(JdbcTemplate jdbc, Clock clock){this.jdbc=jdbc;this.clock=clock;}

    @Transactional(readOnly = true)
    public WorkloadContext capture(String tenantId,String sourceSystem,String apiResource,String workloadPurpose,boolean synthetic){
        Authentication authentication=SecurityContextHolder.getContext().getAuthentication();
        if(!(authentication instanceof IamRuntimeAuthenticationToken token) || !authentication.isAuthenticated()) throw new IllegalStateException("HUMAN_WORKLOAD_AUTHENTICATION_REQUIRED");
        AuthenticationContext auth=token.getPrincipal();
        if(auth.principal().principalType()!=PrincipalRef.PrincipalType.USER) throw new IllegalStateException("HUMAN_WORKLOAD_USER_PRINCIPAL_REQUIRED");
        String authoritativeTenant=auth.activeTenant().tenantId();
        if(tenantId==null||!authoritativeTenant.equals(tenantId.trim())) throw new IllegalStateException("TENANT_CONTEXT_MISMATCH");
        OpenDispatchRequestContext http=OpenDispatchRequestContextHolder.current().orElse(null);
        String userId=auth.principal().principalId();
        Organization organization;
        try(IamTenantContextHolder.Scope ignored=IamTenantContextHolder.open(new IamTenantExecutionContext(authoritativeTenant,userId))){
            bindDatabaseTenantContext(authoritativeTenant,userId);
            organization=organization(authoritativeTenant,userId);
        }
        SecurityEpoch epoch=auth.securityEpoch();
        HttpServletRequest request=currentRequest();
        String requestId=http==null?"":http.requestId();
        String correlation=http==null?"":http.correlationId();
        String ip=http==null?"":http.clientAddress();
        String decisionId=attribute(request,"r3.authorization.decisionId");
        return new WorkloadContext(authoritativeTenant,"USER",userId,"USER",userId,token.credentialId(),"",organization.departmentId(),organization.groupId(),
                "USER_MEMBERSHIP",organization.status(),sourceSystem,apiResource,ip,decisionId,epoch.globalEpoch(),epoch.tenantEpoch(),epoch.principalEpoch(),requestId,correlation,traceId(request),
                token.authenticationMethod(),workloadPurpose,synthetic,clock.instant());
    }


    private HttpServletRequest currentRequest(){
        var attributes=RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }

    private String attribute(HttpServletRequest request,String name){
        if(request==null)return "";
        Object value=request.getAttribute(name);
        return value==null?"":String.valueOf(value).trim();
    }

    private String traceId(HttpServletRequest request){
        if(request==null)return "";
        String traceparent=request.getHeader("traceparent");
        if(traceparent==null)return "";
        String[] parts=traceparent.split("-");
        return parts.length>=4&&parts[1].matches("[0-9a-fA-F]{32}")&&!parts[1].matches("0{32}")?parts[1].toLowerCase(java.util.Locale.ROOT):"";
    }

    private void bindDatabaseTenantContext(String tenant,String actor){
        jdbc.queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenant);
        jdbc.queryForObject("select set_config('app.current_actor_id', ?, true)", String.class, actor);
    }

    private Organization organization(String tenant,String user){
        List<String> departments=jdbc.query("""
                select department_id
                from org_department_memberships
                where tenant_id=?
                  and user_id=?
                  and status='ACTIVE'
                  and is_primary=true
                  and effective_at<=now()
                  and (expires_at is null or expires_at>now())
                order by department_id
                limit 1
                """, (rs,n)->rs.getString(1), tenant, user);
        String department=departments.isEmpty()?"UNASSIGNED":departments.get(0);
        if("UNASSIGNED".equals(department)) return new Organization(department,"","UNRESOLVED");
        List<String> groups=jdbc.query("""
                select gm.group_id
                from org_group_memberships gm
                join organization_groups g
                  on g.tenant_id=gm.tenant_id
                 and g.group_id=gm.group_id
                where gm.tenant_id=?
                  and gm.user_id=?
                  and gm.status='ACTIVE'
                  and gm.effective_at<=now()
                  and (gm.expires_at is null or gm.expires_at>now())
                  and g.status='ACTIVE'
                  and g.owner_department_id=?
                order by gm.group_id
                limit 2
                """, (rs,n)->rs.getString(1), tenant, user, department);
        String group=groups.size()==1?groups.get(0):"";
        return new Organization(department,group,"RESOLVED");
    }
    private record Organization(String departmentId,String groupId,String status){}
}
