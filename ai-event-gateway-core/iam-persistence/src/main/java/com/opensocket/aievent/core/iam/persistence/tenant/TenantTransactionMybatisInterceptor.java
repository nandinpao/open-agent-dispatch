package com.opensocket.aievent.core.iam.persistence.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Properties;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Enforces actual Spring transaction + SET LOCAL before any tenant-owned IAM mapper statement. */
@Intercepts({
    @Signature(type=Executor.class, method="update", args={MappedStatement.class, Object.class}),
    @Signature(type=Executor.class, method="query", args={MappedStatement.class, Object.class,
        org.apache.ibatis.session.RowBounds.class, org.apache.ibatis.session.ResultHandler.class}),
    @Signature(type=Executor.class, method="query", args={MappedStatement.class, Object.class,
        org.apache.ibatis.session.RowBounds.class, org.apache.ibatis.session.ResultHandler.class,
        org.apache.ibatis.cache.CacheKey.class, org.apache.ibatis.mapping.BoundSql.class})
})
public final class TenantTransactionMybatisInterceptor implements Interceptor {
    static final String TENANT_MAPPER_MARKER = "IamTenantOrganizationDao.";
    static final String AUTHENTICATION_MAPPER_MARKER = "IamAuthenticationDao.";
    static final String RBAC_MAPPER_MARKER = "IamRbacDao.";
    static final String API_RUNTIME_MAPPER_MARKER = "IamApiRuntimeDao.";
    static final String TOKEN_MAPPER_MARKER = "IamTokenDao.";
    static final String TASK_MAPPER_NAMESPACE_MARKER = ".persistence.task.dao.";
    static final String AGENT_GOVERNANCE_MAPPER_MARKER = "AgentGovernanceDao.";
    static final String AGENT_ASSIGNMENT_MAPPER_MARKER = "AgentAssignmentDao.";
    private static final java.util.Set<String> CANONICAL_CAPABILITY_RLS_METHODS = java.util.Set.of(
        "findCanonicalCapabilityByCode", "searchCanonicalCapabilities"
    );
    private static final java.util.Set<String> TENANT_AUTHENTICATION_METHODS = java.util.Set.of(
        "findTenantPasswordPolicy", "insertTenantPasswordPolicy", "updateTenantPasswordPolicy",
        "findTenantSessionPolicy", "insertTenantSessionPolicy", "updateTenantSessionPolicy",
        "countActiveTenantMembership", "findTenantSecurityEpoch", "incrementTenantSecurityEpoch",
        "findTenantSession", "findActiveTenantSessions", "insertTenantSession", "updateTenantSession",
        "revokeAllTenantSessions", "findTenantReauthenticationGrant",
        "insertTenantReauthenticationGrant", "updateTenantReauthenticationGrant"
    );


    private static final java.util.Set<String> INSTANCE_TOKEN_METHODS = java.util.Set.of(
        "findMachineCredentialDirectory", "findPersonalAccessTokenDirectory", "findActiveMachineSigningKey", "findPublishableMachineSigningKeys",
        "activateMachineSigningKey", "tryAcquireMachineOauthRateLimit", "insertMachineTokenAudit", "insertMachineResourceAccessAudit"
    );

    private static final java.util.Set<String> CONTEXT_REQUIRED_API_RUNTIME_METHODS = java.util.Set.of(
        "listTenants", "listRootTenantChoices"
    );

    /**
     * Tenant-context queries whose SQL does not carry an explicit tenantId parameter.
     *
     * Most IamApiRuntimeDao methods are detected structurally from their MyBatis
     * parameter map. Keep this set deliberately small: it is only for projections
     * that inherit the already authenticated Tenant context without a tenantId
     * argument of their own.
     */
    private static final java.util.Set<String> CONTEXT_INHERITING_API_RUNTIME_METHODS = java.util.Set.of(
        "listCredentialApiProducts", "listCredentialAudiences", "listCredentialMachineScopes"
    );


    private static final java.util.Set<String> INSTANCE_RBAC_METHODS = java.util.Set.of(
        "findActiveCatalogRevision", "findActiveCatalogRevisionPointerVersion", "findCatalogRevision", "findCatalogRevisions",
        "countDraftCatalogRevisions", "nextCatalogRevisionNumber", "insertCatalogRevision", "cloneCatalogEntries", "cloneCatalogAliases",
        "findCatalogDefinitions", "findCatalogDefinition", "insertCatalogDefinition", "updateCatalogDefinition", "deleteCatalogDefinition",
        "findCatalogAliases", "findCatalogAlias", "insertCatalogAlias", "updateCatalogAlias", "deleteCatalogAlias",
        "findRoleReferencedPermissionCodes", "insertCatalogChangeEvent", "publishCatalogRevision", "supersedeCatalogRevision",
        "setCatalogPublicationContext", "upsertActivePermissionDefinition", "activateCatalogRevision", "insertCatalogPublication",
        "findCatalogPublications", "setPermissionReadinessContext", "findEntryPoints", "findEntryPoint", "summarizeEntryPoints",
        "updateEntryPoint", "findLegacyAuthorityMappings", "findLegacyAuthorityMapping", "updateLegacyAuthorityMapping",
        "findEntryPointBypasses", "findEntryPointBypass", "insertEntryPointBypass", "revokeEntryPointBypass",
        "findApplicationManifests", "findApplicationManifest", "findApplicationManifestEntries", "summarizeApplicationManifestDrift",
        "findPermissionCoverageEvidence", "insertApplicationManifest", "insertApplicationManifestEntry", "activateApplicationManifest"
    );

    private static final java.util.Set<String> TENANT_RBAC_METHODS = java.util.Set.of(
        "findRoleById", "findRoleByCode", "findRolesByIds", "insertRole", "updateRole",
        "findRolePermissions", "insertRolePermission", "findBindingById",
        "findEffectiveBindings", "insertBinding", "updateBinding", "findPolicyVersion",
        "findActiveGroupIds", "findEffectiveDepartmentIds", "insertDecisionAudit",
        "insertShadowDecision", "upsertShadowAggregate",
        "findActiveUserIdsForGroup", "findActiveSeparationOfDutiesRules", "r7ScopesOverlap", "r7ScopeContains",
        "findRbacApproval", "findRbacApprovals", "insertRbacApproval", "updateRbacApproval",
        "insertRbacChangeEvidence"
    );

    @Override public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        if (!requiresTenantContext(statement.getId(), invocation.getArgs().length > 1 ? invocation.getArgs()[1] : null)) return invocation.proceed();
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("TENANT_TRANSACTION_REQUIRED: " + statement.getId());
        }
        IamTenantExecutionContext context = IamTenantContextHolder.require();
        Executor executor = (Executor) invocation.getTarget();
        Connection connection = executor.getTransaction().getConnection();
        try (PreparedStatement ps = connection.prepareStatement(
                "select set_config('app.current_tenant_id', ?, true), set_config('app.current_actor_id', ?, true)")) {
            ps.setString(1, context.tenantId());
            ps.setString(2, context.actorId());
            ps.execute();
        }
        return invocation.proceed();
    }
    private boolean requiresTenantContext(String statementId, Object parameter) {
        if (statementId.contains(TENANT_MAPPER_MARKER)) return true;
        int separator = statementId.lastIndexOf('.');
        String method = separator < 0 ? statementId : statementId.substring(separator + 1);
        if (statementId.contains(AUTHENTICATION_MAPPER_MARKER)) {
            return TENANT_AUTHENTICATION_METHODS.contains(method);
        }
        if (statementId.contains(RBAC_MAPPER_MARKER)) {
            if (INSTANCE_RBAC_METHODS.contains(method)) return true;
            if (TENANT_RBAC_METHODS.contains(method)) {
                if ("insertDecisionAudit".equals(method) || "insertShadowDecision".equals(method)) return true;
                return hasTenantId(parameter);
            }
        }
        if (statementId.contains(TOKEN_MAPPER_MARKER)) {
            // Phase 8C mixes instance-level bootstrap/JWK/audit statements with Tenant-owned
            // credential statements in one mapper. The explicit instance set must stay tiny;
            // every other token statement carrying tenantId requires transaction-local RLS context.
            if (INSTANCE_TOKEN_METHODS.contains(method)) return false;
            return hasTenantId(parameter);
        }
        if (statementId.contains(AGENT_ASSIGNMENT_MAPPER_MARKER)
                && CANONICAL_CAPABILITY_RLS_METHODS.contains(method)) {
            // capability_definitions is FORCE-RLS and its policy calls iam_current_tenant_id().
            // The HTTP request already established the authoritative Tenant in IamTenantContextHolder;
            // bind that same Tenant into PostgreSQL before canonical Capability projections execute.
            return true;
        }
        if (statementId.contains(AGENT_GOVERNANCE_MAPPER_MARKER)
                && ("upsertProfile".equals(method) || "bumpProfilePolicyVersion".equals(method))) {
            // Agent profile writes execute Phase 12.2 ownership triggers that read FORCE-RLS
            // IAM/organization tables. The HTTP/request boundary already resolves the authoritative
            // Tenant into IamTenantContextHolder; bind that context into PostgreSQL before the
            // INSERT/UPDATE statement so nested trigger queries see the same Tenant.
            //
            // Keep this scoped to governed profile mutation/revision writes rather than every
            // AgentGovernanceDao statement: list/detail reads stay usable outside a transaction.
            // bumpProfilePolicyVersion is the credential-rotation boundary and intentionally
            // establishes SET LOCAL before credential revoke/insert statements in the same TX.
            return true;
        }
        if (statementId.contains(TASK_MAPPER_NAMESPACE_MARKER)) {
            // Task/lineage persistence can fire Phase 12.6 FORCE-RLS analytics triggers.
            // Bind the already-authoritative Tenant context when the caller has entered
            // a tenant-scoped workload boundary. Do not infer Tenant authority from mapper
            // parameters and do not turn INSTANCE/background scans into tenant writes.
            return IamTenantContextHolder.current()
                    .map(context -> context.tenantId() != null
                            && !context.tenantId().isBlank()
                            && !"INSTANCE".equalsIgnoreCase(context.tenantId()))
                    .orElse(false);
        }
        if (statementId.contains(API_RUNTIME_MAPPER_MARKER)) {
            if (CONTEXT_REQUIRED_API_RUNTIME_METHODS.contains(method)) return true;

            // Do not maintain a second hand-written list of every Tenant-owned
            // projection. MyBatis exposes @Param("tenantId") and row.tenantId in
            // the parameter map, so any current or future mapper statement that
            // carries Tenant identity must receive SET LOCAL before its SQL/RLS
            // evaluation. This closes omissions such as tenantWorkspaceSummary,
            // accessLifecycleSummary and securityWorkspaceSummary.
            if (hasTenantId(parameter)) return true;

            // A very small number of catalog projections inherit the authenticated
            // Tenant scope and therefore have no tenantId argument. They still need
            // the same transaction-local PostgreSQL context when called inTenant().
            return CONTEXT_INHERITING_API_RUNTIME_METHODS.contains(method)
                    && IamTenantContextHolder.current().isPresent();
        }
        return false;
    }
    private boolean hasTenantId(Object parameter) {
        if (!(parameter instanceof java.util.Map<?,?> map)) return false;

        // MyBatis MapperMethod.ParamMap throws BindingException from get(key)
        // when the key is absent. Always guard optional parameter aliases with
        // containsKey before reading them. Row-based mapper methods such as
        // insertBinding(@Param("row") Map<...>) expose only row/param1 and keep
        // tenantId inside the row map.
        Object direct = valueIfPresent(map, "tenantId");
        if (hasText(direct)) return true;

        Object row = valueIfPresent(map, "row");
        if (row instanceof java.util.Map<?,?> values) {
            return hasText(valueIfPresent(values, "tenantId"));
        }
        return false;
    }

    private Object valueIfPresent(java.util.Map<?,?> values, String key) {
        return values.containsKey(key) ? values.get(key) : null;
    }

    private boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }
    @Override public Object plugin(Object target) { return Plugin.wrap(target, this); }
    @Override public void setProperties(Properties properties) { }
}
