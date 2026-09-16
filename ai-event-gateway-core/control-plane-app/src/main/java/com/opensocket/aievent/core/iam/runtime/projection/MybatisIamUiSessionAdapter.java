package com.opensocket.aievent.core.iam.runtime.projection;

import com.opensocket.aievent.core.iam.api.application.port.IamUiSessionApiPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.response.IamUiSessionResponse;
import com.opensocket.aievent.core.iam.api.response.IamEffectiveAuthorityResponse;
import com.opensocket.aievent.core.iam.api.application.service.IamEffectiveAccessQueryService;
import com.opensocket.aievent.core.iam.api.response.TenantChoiceResponse;
import com.opensocket.aievent.core.iam.authentication.application.port.out.PasswordCredentialRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.RootBootstrapStateRepository;
import com.opensocket.aievent.core.iam.authentication.domain.CredentialSubjectType;
import com.opensocket.aievent.core.iam.authentication.domain.PasswordCredential;
import com.opensocket.aievent.core.iam.authentication.domain.RootBootstrapState;
import com.opensocket.aievent.core.iam.persistence.dao.IamApiRuntimeDao;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.iam.runtime.orchestration.IamAuthenticationRuntimeOrchestrator;
import com.opensocket.aievent.core.iam.security.contract.SubjectRef;
import java.util.*;
import org.springframework.transaction.support.TransactionTemplate;

public final class MybatisIamUiSessionAdapter implements IamUiSessionApiPort {
    private final IamApiRuntimeDao dao;
    private final TransactionTemplate transactions;
    private final PasswordCredentialRepository credentials;
    private final RootBootstrapStateRepository bootstrapStates;
    private final IamEffectiveAccessQueryService effectiveAccess;

    public MybatisIamUiSessionAdapter(
            IamApiRuntimeDao dao,
            TransactionTemplate transactions,
            PasswordCredentialRepository credentials,
            RootBootstrapStateRepository bootstrapStates,
            IamEffectiveAccessQueryService effectiveAccess) {
        this.dao = dao;
        this.transactions = transactions;
        this.credentials = credentials;
        this.bootstrapStates = bootstrapStates;
        this.effectiveAccess = Objects.requireNonNull(effectiveAccess);
    }

    @Override
    public IamUiSessionResponse current(IamApiRequestContext context) {
        var authentication = context.requireAuthentication();
        String userId = authentication.subject().subjectId();
        if (authentication.subject().identityType() == SubjectRef.IdentityType.INSTANCE_ROOT) {
            PasswordCredential credential = credentials.find(CredentialSubjectType.INSTANCE_ROOT, "root")
                    .orElseThrow(() -> new IllegalArgumentException("AUTH_CREDENTIAL_NOT_FOUND"));
            Set<String> actions = new LinkedHashSet<>();
            boolean passwordChangeLimited = authentication.assurance().methods().contains(
                    IamAuthenticationRuntimeOrchestrator.PASSWORD_CHANGE_REQUIRED_METHOD);
            boolean bootstrapLimited = authentication.assurance().methods().contains(
                    IamAuthenticationRuntimeOrchestrator.ROOT_BOOTSTRAP_REQUIRED_METHOD);
            if (credential.mustChange() || passwordChangeLimited) actions.add("CHANGE_PASSWORD");
            RootBootstrapState state = bootstrapStates.find();
            if (!credential.mustChange() && state.status() != RootBootstrapState.Status.COMPLETED) {
                actions.add("COMPLETE_BOOTSTRAP");
            }
            List<TenantChoiceResponse> rootTenantChoices = rootTenantChoices(userId);
            return new IamUiSessionResponse(
                    "CANONICAL_SESSION", userId, "root", "Instance Root", Set.of("INSTANCE_ROOT"),
                    passwordChangeLimited || bootstrapLimited ? Set.of() : rootPermissions(),
                    passwordChangeLimited || bootstrapLimited ? Map.of() : rootPermissionScopes(),
                    "", rootTenantChoices, actions,
                    credential.version(), authentication.issuedAt(), authentication.expiresAt(),
                    authentication.assurance().methods());
        }

        Map<String,Object> identity = transactions.execute(status -> dao.findUiIdentity(userId));
        if (identity == null) throw new IllegalArgumentException("IDENTITY_USER_NOT_FOUND");
        String tenantId = authentication.activeTenant().tenantId();
        IamEffectiveAuthorityResponse authority = effectiveAccess.effectiveAuthority(tenantId, userId);
        Set<String> roles = authority.roleCodes();
        Set<String> permissions = authority.permissionCodes();
        List<TenantChoiceResponse> choices = new ArrayList<>();
        List<Map<String,Object>> rows = transactions.execute(status -> dao.activeTenantChoices(userId));
        if (rows != null) for (Map<String,Object> row : rows) {
            String id = text(row, "tenantId");
            Set<String> summary = effectiveAccess.effectiveAuthority(id, userId).roleCodes();
            choices.add(new TenantChoiceResponse(id, text(row,"tenantCode"), text(row,"tenantName"),
                    text(row,"membershipStatus"), summary));
        }
        Set<String> actions = new LinkedHashSet<>();
        String accountStatus = text(identity,"status");
        PasswordCredential credential = credentials.find(CredentialSubjectType.HUMAN_USER, userId).orElse(null);
        if (credential != null && credential.mustChange()) actions.add("CHANGE_PASSWORD");
        if ("PASSWORD_RESET_REQUIRED".equals(accountStatus)) actions.add("CHANGE_PASSWORD");
        if ("MFA_ENROLLMENT_REQUIRED".equals(accountStatus)) actions.add("ENROLL_MFA");
        return new IamUiSessionResponse(
                "CANONICAL_SESSION", userId, text(identity,"username"), text(identity,"displayName"),
                roles, permissions, authority.permissionScopes(), tenantId, choices, actions,
                credential == null ? 0L : credential.version(),
                authentication.issuedAt(), authentication.expiresAt(),
                authentication.assurance().methods());
    }


    private List<TenantChoiceResponse> rootTenantChoices(String userId) {
        return IamTenantContextHolder.withContext(
                new IamTenantExecutionContext("INSTANCE", "ui-session:" + userId),
                () -> transactions.execute(status -> {
                    List<Map<String,Object>> rows = dao.listRootTenantChoices();
                    if (rows == null) return List.of();
                    return rows.stream().map(row -> new TenantChoiceResponse(
                            text(row,"tenantId"), text(row,"tenantCode"), text(row,"tenantName"),
                            text(row,"membershipStatus"), Set.of("INSTANCE_ROOT"))).toList();
                }));
    }

    private Set<String> rootPermissions() {
        List<String> codes = transactions.execute(status -> dao.activeInstancePermissionCodes());
        return codes == null ? Set.of() : Set.copyOf(codes);
    }

    private Map<String, Set<String>> rootPermissionScopes() {
        Map<String, Set<String>> scopes = new TreeMap<>();
        for (String permission : rootPermissions()) {
            scopes.put(permission, Set.of("INSTANCE:INSTANCE"));
        }
        return Map.copyOf(scopes);
    }
    private static String text(Map<String,Object> row,String key) {
        Object value=row.get(key);return value==null?"":String.valueOf(value);
    }
}
