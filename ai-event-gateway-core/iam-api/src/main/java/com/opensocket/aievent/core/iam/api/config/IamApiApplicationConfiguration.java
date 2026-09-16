package com.opensocket.aievent.core.iam.api.config;

import com.opensocket.aievent.core.iam.api.application.service.IamOrganizationAdministrationService;
import com.opensocket.aievent.core.iam.api.application.service.IamPeopleBulkAdministrationService;
import com.opensocket.aievent.core.iam.api.application.service.IamRbacAdministrationService;
import com.opensocket.aievent.core.iam.api.application.service.IamRbacHardeningService;
import com.opensocket.aievent.core.iam.api.application.service.IamPermissionCatalogAdministrationService;
import com.opensocket.aievent.core.iam.api.application.service.IamEntryPointAuthorityAdministrationService;
import com.opensocket.aievent.core.iam.api.application.service.IamTokenAdministrationService;
import com.opensocket.aievent.core.iam.api.application.service.IamEffectiveAccessQueryService;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.api.application.port.IamAdministrationProjectionPort;
import com.opensocket.aievent.core.iam.api.application.port.IamPlatformUserAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamSessionAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamUserInvitationApiPort;
import com.opensocket.aievent.core.iam.api.security.IamPermissionGuard;
import com.opensocket.aievent.core.iam.organization.application.port.in.DepartmentCommandPort;
import com.opensocket.aievent.core.iam.organization.application.port.in.GroupCommandPort;
import com.opensocket.aievent.core.iam.organization.application.port.in.OrganizationQueryPort;
import com.opensocket.aievent.core.iam.organization.application.port.in.TenantCommandPort;
import com.opensocket.aievent.core.iam.rbac.application.port.in.RbacAdministrationPort;
import com.opensocket.aievent.core.iam.rbac.application.port.in.PermissionCatalogAdministrationPort;
import com.opensocket.aievent.core.iam.rbac.application.port.in.EntryPointAuthorityAdministrationPort;
import com.opensocket.aievent.core.iam.rbac.application.port.out.PrincipalExpansionPort;
import com.opensocket.aievent.core.iam.rbac.application.port.out.PrincipalRoleBindingRepository;
import com.opensocket.aievent.core.iam.rbac.application.port.out.RoleRepository;
import com.opensocket.aievent.core.iam.rbac.application.port.out.RolePermissionRepository;
import com.opensocket.aievent.core.iam.rbac.application.port.out.PermissionCatalogRepository;
import com.opensocket.aievent.core.iam.rbac.application.port.out.OrganizationScopePort;
import com.opensocket.aievent.core.iam.rbac.application.port.out.RbacHardeningInspectionPort;
import com.opensocket.aievent.core.iam.rbac.application.port.out.RbacCriticalApprovalRepository;
import com.opensocket.aievent.core.iam.rbac.application.port.out.RbacChangeEvidencePort;
import com.opensocket.aievent.core.iam.rbac.application.port.out.RbacEventPublisher;
import com.opensocket.aievent.core.iam.rbac.application.port.out.TenantRbacExecutionPort;
import com.opensocket.aievent.core.iam.api.security.R7SensitiveOperationGuard;
import java.time.Clock;
import com.opensocket.aievent.core.iam.token.application.port.in.AccessTokenCommandPort;
import com.opensocket.aievent.core.iam.token.application.port.in.ServiceAccountCommandPort;
import com.opensocket.aievent.core.iam.token.application.port.in.ServiceAccountCredentialCommandPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "aeg.iam.api", name = "enabled", havingValue = "true")
public class IamApiApplicationConfiguration {
    @Bean
    IamOrganizationAdministrationService organizationApi(
            TenantCommandPort tenants,
            DepartmentCommandPort departments,
            GroupCommandPort groups,
            OrganizationQueryPort queries,
            IamIdempotencyExecutor idempotency) {
        return new IamOrganizationAdministrationService(
                tenants, departments, groups, queries, idempotency);
    }

    @Bean
    IamPeopleBulkAdministrationService peopleBulkApi(
            IamOrganizationAdministrationService organization,
            IamAdministrationProjectionPort projections,
            IamPlatformUserAdministrationApiPort platformUsers,
            IamSessionAdministrationApiPort sessions,
            IamUserInvitationApiPort invitations,
            IamPermissionGuard guard) {
        return new IamPeopleBulkAdministrationService(organization, projections, platformUsers, sessions, invitations, guard);
    }

    @Bean
    IamRbacHardeningService rbacHardeningApi(PrincipalExpansionPort expansion,PrincipalRoleBindingRepository bindings,
            RoleRepository roles,RolePermissionRepository rolePermissions,PermissionCatalogRepository permissions,
            RbacHardeningInspectionPort inspection,RbacCriticalApprovalRepository approvals,
            RbacChangeEvidencePort evidence,RbacEventPublisher events,Clock clock,
            TenantRbacExecutionPort execution) {
        return new IamRbacHardeningService(
                expansion,bindings,roles,rolePermissions,permissions,inspection,approvals,evidence,events,clock,execution);
    }

    @Bean
    R7SensitiveOperationGuard r7SensitiveOperationGuard(RbacEventPublisher events,Clock clock) {
        return new R7SensitiveOperationGuard(events,clock);
    }

    @Bean
    IamRbacAdministrationService rbacApi(
            RbacAdministrationPort port, IamIdempotencyExecutor idempotency, IamRbacHardeningService hardening) {
        return new IamRbacAdministrationService(port, idempotency, hardening);
    }

    @Bean
    IamPermissionCatalogAdministrationService permissionCatalogApi(
            PermissionCatalogAdministrationPort port,
            IamIdempotencyExecutor idempotency) {
        return new IamPermissionCatalogAdministrationService(port, idempotency);
    }

    @Bean
    IamEntryPointAuthorityAdministrationService entryPointAuthorityApi(EntryPointAuthorityAdministrationPort port,IamIdempotencyExecutor idempotency) {
        return new IamEntryPointAuthorityAdministrationService(port,idempotency);
    }

    @Bean
    IamEffectiveAccessQueryService effectiveAccessApi(
            PrincipalExpansionPort expansion,
            PermissionCatalogRepository permissions,
            OrganizationScopePort organizationScope,
            PrincipalRoleBindingRepository bindings,
            RoleRepository roles,
            RolePermissionRepository rolePermissions,
            Clock clock,
            TenantRbacExecutionPort execution) {
        return new IamEffectiveAccessQueryService(
                expansion, permissions, organizationScope, bindings, roles, rolePermissions, clock, execution);
    }


    @Bean
    IamTokenAdministrationService tokenApi(
            AccessTokenCommandPort tokens,
            ServiceAccountCommandPort accounts,
            ServiceAccountCredentialCommandPort credentials,
            RbacAdministrationPort rbac,
            IamIdempotencyExecutor idempotency,
            Clock clock) {
        return new IamTokenAdministrationService(tokens, accounts, credentials, rbac, idempotency, clock);
    }
}
