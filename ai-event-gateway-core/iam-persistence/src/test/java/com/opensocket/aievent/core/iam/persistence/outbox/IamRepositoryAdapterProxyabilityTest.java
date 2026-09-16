package com.opensocket.aievent.core.iam.persistence.outbox;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.List;

import org.junit.jupiter.api.Test;

class IamRepositoryAdapterProxyabilityTest {

    @Test
    void repositoryAdaptersMustRemainSubclassableForSpringAop() {
        List<Class<?>> adapters = List.of(
                com.opensocket.aievent.core.iam.persistence.outbox.IamTransactionalOutboxWriter.class,
                com.opensocket.aievent.core.iam.persistence.outbox.MybatisAuthenticationEventPublisher.class,
                com.opensocket.aievent.core.iam.persistence.outbox.MybatisIdentityEventPublisher.class,
                com.opensocket.aievent.core.iam.persistence.outbox.MybatisOrganizationEventPublisher.class,
                com.opensocket.aievent.core.iam.persistence.outbox.MybatisRbacEventPublisher.class,
                com.opensocket.aievent.core.iam.persistence.outbox.MybatisTokenEventPublisher.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisAccessTokenRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisAuthenticationStateRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisAuthenticationSubjectAdapter.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisAuthorizationDecisionAuditAdapter.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisBrowserSessionRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisDepartmentHierarchyRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisDepartmentMembershipRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisDepartmentRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisDepartmentRevisionRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisGroupMembershipRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisGroupRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisHumanUserRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisMfaAuthenticationRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisMachineTokenRuntimeAdapter.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisOrganizationScopeAdapter.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisOrganizationSnapshotRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisPasswordAuthenticationRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisPermissionCatalogRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisPolicyVersionRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisPrincipalExpansionAdapter.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisPrincipalRoleBindingRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisReauthenticationGrantRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisRolePermissionRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisRoleRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisRootAuthenticationRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisRootIdentityRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisSecurityEpochAdapter.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisSecurityEpochAuthorityAdapter.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisServiceAccountRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisServiceAccountCredentialRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisSessionPolicyRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisTenantMembershipRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisTenantRepository.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisTokenGovernanceAdapter.class,
                com.opensocket.aievent.core.iam.persistence.repository.MybatisTokenSecurityEpochAdapter.class
        );
        for (Class<?> adapter : adapters) {
            assertFalse(
                    Modifier.isFinal(adapter.getModifiers()),
                    "@DatabaseRepositoryAdapter must remain subclassable for class-based Spring AOP: "
                            + adapter.getName()
            );
        }
    }
}
