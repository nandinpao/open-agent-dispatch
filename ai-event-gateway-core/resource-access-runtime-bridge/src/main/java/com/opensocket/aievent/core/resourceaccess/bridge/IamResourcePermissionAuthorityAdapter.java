package com.opensocket.aievent.core.resourceaccess.bridge;

import com.opensocket.aievent.core.iam.rbac.application.port.in.AuthorizationPort;
import com.opensocket.aievent.core.iam.rbac.application.port.in.EffectivePermissionScopePort;
import com.opensocket.aievent.core.iam.rbac.application.port.in.EffectivePermissionScopeQuery;
import com.opensocket.aievent.core.iam.rbac.domain.EffectivePermissionScopeGrant;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.agent.governance.AgentAuthorizationScope;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceRepository;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/** Uses IAM RBAC as permission/scope authority while Resource Access remains the resource-scope authority. */
@Component
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public final class IamResourcePermissionAuthorityAdapter implements ResourcePermissionAuthorityPort {
    private final AuthorizationPort iam;
    private final EffectivePermissionScopePort effectiveScopes;

    @Autowired(required=false)
    private AgentGovernanceRepository agentGovernanceRepository;
    @Autowired(required=false)
    private TaskRepository taskRepository;

    public IamResourcePermissionAuthorityAdapter(AuthorizationPort iam, EffectivePermissionScopePort effectiveScopes) {
        this.iam = Objects.requireNonNull(iam, "iam");
        this.effectiveScopes = Objects.requireNonNull(effectiveScopes, "effectiveScopes");
    }

    /** Point authorization is retained for detail/action decisions. */
    @Override
    public ResourcePermissionDecision evaluate(AuthorizationRequest request, PrincipalScopeSnapshot scope) {
        ResourcePermissionDecision agentService = evaluateAgentService(request);
        if (agentService != null) return agentService;
        List<Candidate> candidates=new ArrayList<>();
        candidates.add(new Candidate("TENANT",request.activeTenant().tenantId()));
        scope.departmentIds().stream().sorted().forEach(id->candidates.add(new Candidate("DEPARTMENT",id)));
        scope.groupIds().stream().sorted().forEach(id->candidates.add(new Candidate("GROUP",id)));
        com.opensocket.aievent.core.iam.security.contract.AuthorizationDecision last=null;
        for(Candidate c:candidates){
            var decision=iam.authorize(new com.opensocket.aievent.core.iam.security.contract.AuthorizationRequest(
                    request.principal(),request.activeTenant(),request.action().permissionCode(),
                    request.resourceRef().resourceType().name(),request.resourceRef().resourceId(),c.type(),c.id(),
                    request.authenticationContext().securityEpoch(),
                    Map.of("correlationId",request.correlationId(),"resourceAccess","P4RA-D")));
            last=decision;
            if(decision.effect()==com.opensocket.aievent.core.iam.security.contract.AuthorizationDecision.Effect.ALLOW)
                return convert(decision,true);
        }
        return last==null
                ? new ResourcePermissionDecision(false,"IAM_PERMISSION_AUTHORITY_UNAVAILABLE",Set.of(),Set.of(),"","")
                : convert(last,false);
    }

    /**
     * RS1 list/search projection. This is intentionally not derived from organization membership or
     * from the first successful point authorization: every effective Role Binding scope is preserved.
     */
    @Override
    public ResourcePermissionScopeDecision resolveEffectiveScopes(
            AuthorizationRequest request, PrincipalScopeSnapshot principalScope) {
        ResourcePermissionDecision agentService = evaluateAgentService(request);
        if (agentService != null) {
            return new ResourcePermissionScopeDecision(agentService.granted(), agentService.reasonCode(), false,
                    Set.of(), Set.of(), Set.of(), agentService.matchedBindingIds(), agentService.matchedRoleIds());
        }
        var result = effectiveScopes.resolve(new EffectivePermissionScopeQuery(
                request.principal(), request.activeTenant(), request.action().permissionCode(),
                request.authenticationContext().securityEpoch()));
        if (!result.granted()) {
            return new ResourcePermissionScopeDecision(false, result.reasonCode(), false,
                    Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        }
        boolean tenant = false;
        Set<String> exact = new LinkedHashSet<>();
        Set<String> subtrees = new LinkedHashSet<>();
        Set<String> groups = new LinkedHashSet<>();
        Set<String> bindingIds = new LinkedHashSet<>();
        Set<String> roleIds = new LinkedHashSet<>();
        for (EffectivePermissionScopeGrant grant : result.grants()) {
            bindingIds.addAll(grant.matchedBindingIds());
            roleIds.addAll(grant.matchedRoleIds());
            switch (grant.scopeType()) {
                case TENANT -> tenant = true;
                case DEPARTMENT -> exact.add(grant.scopeId());
                case DEPARTMENT_SUBTREE -> subtrees.add(grant.scopeId());
                case GROUP -> groups.add(grant.scopeId());
                case INSTANCE -> { /* INSTANCE is never projected into Tenant business-data queries. */ }
            }
        }
        return new ResourcePermissionScopeDecision(true, result.reasonCode(), tenant,
                exact, subtrees, groups, bindingIds, roleIds);
    }


    /**
     * Runtime authority for canonical Agent machine principals. Dispatch Access (stored in the historical
     * AgentAuthorizationScope shape) is not a routing ACL and never selects a candidate Agent. It only grants
     * the post-assignment Task read/execute permission ceiling; exact Task assignment is still required by
     * Resource Access AGENT_ASSIGNMENT participant evidence.
     */
    private ResourcePermissionDecision evaluateAgentService(AuthorizationRequest request) {
        var principalType = request.principal().principalType();
        if ((principalType != com.opensocket.aievent.core.iam.security.contract.PrincipalRef.PrincipalType.AGENT
                && principalType != com.opensocket.aievent.core.iam.security.contract.PrincipalRef.PrincipalType.A2A_AGENT)
                || request.resourceRef().resourceType() != ResourceType.TASK) {
            return null;
        }
        String permission = request.action().permissionCode();
        if (!("task.read".equals(permission) || "task.execute".equals(permission) || "a2a.request.create".equals(permission))) {
            return new ResourcePermissionDecision(false,"RS4_AGENT_SERVICE_PERMISSION_DENIED",Set.of(),Set.of(),"","");
        }
        if (agentGovernanceRepository == null || taskRepository == null) {
            return new ResourcePermissionDecision(false,"RS4_AGENT_SERVICE_AUTHORITY_UNAVAILABLE",Set.of(),Set.of(),"","");
        }
        String agentId = request.principal().principalId();
        AgentProfile profile = agentGovernanceRepository.findProfile(agentId).orElse(null);
        TaskRecord task = taskRepository.findByTenantAndId(request.activeTenant().tenantId(), request.resourceRef().resourceId()).orElse(null);
        if (profile == null || task == null || !profile.allowsConnection()
                || !request.activeTenant().tenantId().equals(profile.getTenantId())
                || !request.activeTenant().tenantId().equals(task.getTenantId())) {
            return new ResourcePermissionDecision(false,"RS4_AGENT_SERVICE_SCOPE_DENIED",Set.of(),Set.of(),"","");
        }
        List<AgentAuthorizationScope> scopes = agentGovernanceRepository.findEnabledScopes(agentId);
        boolean matched = scopes.stream().anyMatch(scope -> matchesAgentScope(scope, task));
        if (!matched) {
            return new ResourcePermissionDecision(false,"RS4_AGENT_SERVICE_TASK_SCOPE_NOT_MATCHED",Set.of(),Set.of(),"","");
        }
        return new ResourcePermissionDecision(true,"RS4_AGENT_SERVICE_RUNTIME_PERMISSION_GRANTED",
                Set.of("AGENT:"+agentId),Set.of("AGENT"),"AGENT_ASSIGNMENT",agentId);
    }

    private boolean matchesAgentScope(AgentAuthorizationScope scope, TaskRecord task) {
        if (scope == null || !scope.isEnabled()) return false;
        if (!blank(scope.getTenantId()) && !scope.getTenantId().equals(task.getTenantId())) return false;
        if (!matchesScopeCode(scope.getSystemCode(), task.getSourceSystem())) return false;
        if (!matchesScopeCode(scope.getSiteCode(), task.getSiteId())) return false;
        if (!matchesScopeCode(scope.getEventType(), task.getEventType())) return false;
        if (!wildcard(scope.getTaskType())
                && !equalsCode(scope.getTaskType(), task.getEffectiveTaskTypeCode())
                && (task.getTaskType()==null || !equalsCode(scope.getTaskType(), task.getTaskType().name()))) return false;
        return true;
    }

    static boolean matchesScopeCode(String scopeCode,String actualCode){
        return wildcard(scopeCode) || equalsCode(scopeCode,actualCode);
    }
    private static boolean wildcard(String value){return blank(value)||"*".equals(value.trim());}
    private static boolean equalsCode(String a,String b){return a!=null&&b!=null&&a.trim().equalsIgnoreCase(b.trim());}
    private static boolean blank(String value){return value==null||value.isBlank();}

    private ResourcePermissionDecision convert(com.opensocket.aievent.core.iam.security.contract.AuthorizationDecision d,boolean granted){
        return new ResourcePermissionDecision(granted,d.reasonCode(),d.matchedBindingIds(),d.matchedRoleIds(),d.effectiveScopeType(),d.effectiveScopeId());
    }
    private record Candidate(String type,String id){}
}
