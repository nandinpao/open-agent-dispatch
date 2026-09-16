package com.opensocket.aievent.core.resourceaccess.enforcement;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.resourceaccess.core.ResourceDecisionEvidenceRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Builds SQL-consumable Issue/Integration scope plans without loading provider resources into Java. */
@Component
@ConditionalOnProperty(prefix="resource-access",name={"enabled","integration-enabled"},havingValue="true")
public final class DefaultIntegrationScopeQueryPlanService implements IntegrationScopeQueryPlanPort {
    private final ResourceDecisionEvidenceRepository evidence;
    private final ResourcePermissionAuthorityPort permissions;
    private final ResourceEnforcementContextPort contexts;
    private final IntegrationScopeQueryAuditPort audit;
    private final MaterializedScopeSnapshotPort snapshots;
    private final ResourceScopeShareRepositoryPort shares;

    public DefaultIntegrationScopeQueryPlanService(ResourceDecisionEvidenceRepository evidence,
            ResourcePermissionAuthorityPort permissions, ResourceEnforcementContextPort contexts,
            ObjectProvider<IntegrationScopeQueryAuditPort> auditProvider,
            ObjectProvider<MaterializedScopeSnapshotPort> snapshotProvider, ResourceScopeShareRepositoryPort shares) {
        this.evidence=Objects.requireNonNull(evidence,"evidence");
        this.permissions=Objects.requireNonNull(permissions,"permissions");
        this.contexts=Objects.requireNonNull(contexts,"contexts");
        this.audit=auditProvider==null?null:auditProvider.getIfAvailable();
        this.snapshots=snapshotProvider==null?null:snapshotProvider.getIfAvailable();
        this.shares=Objects.requireNonNull(shares,"shares");
    }

    @Override
    public IntegrationScopeQueryPlan build(String permissionCode,ResourceType resourceType,
            VisibilityLevel requestedVisibility,String purpose) {
        requireSupported(resourceType);
        ResourceEnforcementContext runtime=contexts.current();
        var auth=runtime.authentication();
        String tenant=auth.activeTenant().tenantId();
        Instant at=runtime.requestedAt();
        PolicyPrincipalRef direct=directPolicyPrincipal(auth.principal());
        PrincipalScopeSnapshot principalScope=evidence.resolvePrincipalScope(tenant,auth.principal(),at);
        ResourceRef synthetic=new ResourceRef(tenant,resourceType,"__INTEGRATION_LIST_SCOPE__");
        AuthorizationRequest permissionRequest=new AuthorizationRequest(auth.principal(),auth,auth.activeTenant(),
                new ResourceAction(required(permissionCode,"permissionCode"),ResourceAction.ActionKind.READ,false),
                synthetic,requestedVisibility==null?VisibilityLevel.METADATA:requestedVisibility,RequestChannel.INTERNAL_PORT,
                required(purpose,"purpose"),OperationPhase.START,"",runtime.correlationId(),SecurityEpoch.ZERO,
                Map.of("queryPlan","INTEGRATION_LIST","resourceType",resourceType.name()));
        ResourcePermissionScopeDecision permission=permissions.resolveEffectiveScopes(permissionRequest,principalScope);
        PolicyVersion policyVersion=evidence.currentPolicyVersion(tenant);
        SecurityEpoch epoch=evidence.currentSecurityEpoch(tenant,auth.principal(),synthetic);
        if(!permission.granted()) return deny(tenant,direct,permissionCode,resourceType,policyVersion,epoch);
        if(snapshots!=null){
            Optional<MaterializedScopeSnapshot> active=snapshots.findActive(tenant,ScopeSnapshotKind.INTEGRATION_LIST,
                    direct.principalType().name(),direct.principalId(),permissionCode,resourceType,policyVersion,epoch,at);
            if(active.isPresent()){
                IntegrationScopeQueryPlan materialized=toIntegrationPlan(active.get());
                if(audit!=null)audit.recordPlan(materialized,purpose+":MATERIALIZED",at);
                return materialized;
            }
        }

        Set<PolicyPrincipalRef> principals=policyPrincipals(auth.principal(),principalScope);
        List<ScopeGrantRecord> grants=evidence.findEffectiveScopeGrants(tenant,principals,permissionCode,resourceType,at);
        List<ExplicitDenyRecord> denies=evidence.findEffectiveExplicitDenies(tenant,principals,permissionCode,resourceType,at);
        Set<String> sharedResourceIds=shares.findEffectiveSharedResourceIds(tenant,resourceType,permission,at);
        Set<String> exact=new LinkedHashSet<>(), subtrees=new LinkedHashSet<>(), groups=new LinkedHashSet<>(), resources=new LinkedHashSet<>(sharedResourceIds), excluded=new LinkedHashSet<>();
        Set<String> deniedDepartments=new LinkedHashSet<>(), deniedSubtrees=new LinkedHashSet<>(), deniedGroups=new LinkedHashSet<>();
        boolean tenantWide=permission.tenantScoped(); VisibilityLevel cap=VisibilityLevel.NONE;
        exact.addAll(permission.exactDepartmentIds());
        subtrees.addAll(permission.subtreeDepartmentRootIds());
        groups.addAll(permission.groupIds());
        for(ScopeGrantRecord grant:grants){
            if(IamScopeAuthorityCeiling.explicitResourceException(grant.scopeType())){
                cap=higher(cap,grant.visibilityLevel());
                if(grant.scopeType()==ScopeType.RESOURCE)resources.add(grant.scopeRefId());
                continue;
            }
            if(!IamScopeAuthorityCeiling.organizationalGrantWithinPermission(permission,grant,tenant,evidence))continue;
            cap=higher(cap,grant.visibilityLevel());
            switch(grant.scopeType()){
                case TENANT -> tenantWide=true;
                case DEPARTMENT -> exact.add(grant.scopeRefId());
                case DEPARTMENT_SUBTREE -> subtrees.add(grant.scopeRefId());
                case GROUP -> groups.add(grant.scopeRefId());
                default -> { }
            }
        }
        for(ExplicitDenyRecord deny:denies){
            switch(deny.scopeType()){
                case TENANT -> { return deny(tenant,direct,permissionCode,resourceType,policyVersion,epoch); }
                case DEPARTMENT -> deniedDepartments.add(deny.scopeRefId());
                case DEPARTMENT_SUBTREE -> deniedSubtrees.add(deny.scopeRefId());
                case GROUP -> deniedGroups.add(deny.scopeRefId());
                case RESOURCE -> excluded.add(deny.scopeRefId());
                default -> { }
            }
        }
        /* Membership selects policy principals; it does not broaden IAM authority. */
        if(cap==VisibilityLevel.NONE) cap=requestedVisibility==null?VisibilityLevel.METADATA:requestedVisibility;
        IntegrationScopeQueryStrategy strategy=tenantWide?IntegrationScopeQueryStrategy.TENANT:
                (!resources.isEmpty()&&exact.isEmpty()&&subtrees.isEmpty()&&groups.isEmpty())?IntegrationScopeQueryStrategy.EXPLICIT_RESOURCE_SET:
                (!subtrees.isEmpty()&&resources.isEmpty()&&groups.isEmpty())?IntegrationScopeQueryStrategy.DIRECT_HIERARCHY_JOIN:IntegrationScopeQueryStrategy.HYBRID;
        String canonical=String.join("|",tenant,direct.principalType().name(),direct.principalId(),permissionCode,resourceType.name(),strategy.name(),
                sorted(exact),sorted(subtrees),sorted(groups),sorted(resources),sorted(excluded),sorted(deniedDepartments),sorted(deniedSubtrees),
                sorted(deniedGroups),policyVersion.toString(),epoch.toString());
        IntegrationScopeQueryPlan plan=new IntegrationScopeQueryPlan(tenant,direct.principalType().name(),direct.principalId(),permissionCode,resourceType,
                strategy,exact,subtrees,groups,resources,excluded,deniedDepartments,deniedSubtrees,deniedGroups,cap,policyVersion,epoch,sha256(canonical));
        if(snapshots!=null)snapshots.savePrepared(toSnapshot(plan,at),runtime.correlationId());
        if(audit!=null)audit.recordPlan(plan,purpose,at); return plan;
    }

    private static MaterializedScopeSnapshot toSnapshot(IntegrationScopeQueryPlan plan,Instant at){
        return new MaterializedScopeSnapshot("mss-"+UUID.randomUUID(),plan.tenantId(),ScopeSnapshotKind.INTEGRATION_LIST,
                plan.principalType(),plan.principalId(),plan.permissionCode(),plan.resourceType(),plan.strategy().name(),
                plan.exactDepartmentIds(),plan.subtreeDepartmentRootIds(),plan.groupIds(),plan.explicitResourceIds(),
                plan.excludedResourceIds(),plan.deniedDepartmentIds(),plan.deniedSubtreeDepartmentRootIds(),plan.deniedGroupIds(),
                plan.maximumVisibility(),plan.policyVersion(),plan.securityEpoch(),plan.securityEpoch().departmentTreeRevision(),
                plan.planHash(),MaterializedScopeSnapshotStatus.PREPARED,at,null,null,0);
    }
    private static IntegrationScopeQueryPlan toIntegrationPlan(MaterializedScopeSnapshot snapshot){
        return new IntegrationScopeQueryPlan(snapshot.tenantId(),snapshot.principalType(),snapshot.principalId(),snapshot.permissionCode(),
                snapshot.resourceType(),IntegrationScopeQueryStrategy.valueOf(snapshot.strategy()),snapshot.exactDepartmentIds(),
                snapshot.subtreeDepartmentRootIds(),snapshot.groupIds(),snapshot.explicitResourceIds(),snapshot.excludedResourceIds(),
                snapshot.deniedDepartmentIds(),snapshot.deniedSubtreeDepartmentRootIds(),snapshot.deniedGroupIds(),
                snapshot.maximumVisibility(),snapshot.policyVersion(),snapshot.securityEpoch(),snapshot.planHash());
    }

    private static void requireSupported(ResourceType type){
        if(type==null||!EnumSet.of(ResourceType.ISSUE_CONNECTION,ResourceType.ISSUE_PRINCIPAL,ResourceType.ISSUE_CREDENTIAL_METADATA,
                ResourceType.ISSUE_PROJECT_MAPPING,ResourceType.TASK_ISSUE_LINK,ResourceType.ISSUE_CONTEXT_SNAPSHOT,
                ResourceType.ISSUE_CONFLICT,ResourceType.ISSUE_DEAD_LETTER,ResourceType.ISSUE_TOPOLOGY).contains(type))
            throw new IllegalArgumentException("Unsupported Issue/Integration list resource type: "+type);
    }
    private static IntegrationScopeQueryPlan deny(String tenant,PolicyPrincipalRef p,String permission,ResourceType type,PolicyVersion v,SecurityEpoch e){
        return new IntegrationScopeQueryPlan(tenant,p.principalType().name(),p.principalId(),permission,type,IntegrationScopeQueryStrategy.DENY_ALL,
                Set.of(),Set.of(),Set.of(),Set.of(),Set.of(),Set.of(),Set.of(),Set.of(),VisibilityLevel.NONE,v,e,
                sha256("DENY|"+tenant+"|"+p.principalId()+"|"+permission+"|"+type));
    }
    private static Set<PolicyPrincipalRef> policyPrincipals(com.opensocket.aievent.core.iam.security.contract.PrincipalRef p,PrincipalScopeSnapshot s){
        Set<PolicyPrincipalRef> r=new LinkedHashSet<>();r.add(directPolicyPrincipal(p));
        s.departmentIds().forEach(id->r.add(new PolicyPrincipalRef(ScopePrincipalType.DEPARTMENT,id)));
        s.groupIds().forEach(id->r.add(new PolicyPrincipalRef(ScopePrincipalType.GROUP,id)));return Set.copyOf(r);
    }
    private static PolicyPrincipalRef directPolicyPrincipal(com.opensocket.aievent.core.iam.security.contract.PrincipalRef p){
        ScopePrincipalType t=switch(p.principalType()){case USER->ScopePrincipalType.USER;case DEPARTMENT->ScopePrincipalType.DEPARTMENT;case GROUP->ScopePrincipalType.GROUP;
            case SERVICE_ACCOUNT,AGENT,A2A_AGENT,INSTANCE_ROOT,SYSTEM_SERVICE->ScopePrincipalType.SERVICE_ACCOUNT;};
        return new PolicyPrincipalRef(t,p.principalId());
    }
    private static VisibilityLevel higher(VisibilityLevel a,VisibilityLevel b){return a.ordinal()>=b.ordinal()?a:b;}
    private static String sorted(Set<String> v){return String.join(",",new TreeSet<>(v));}
    private static String sha256(String v){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
