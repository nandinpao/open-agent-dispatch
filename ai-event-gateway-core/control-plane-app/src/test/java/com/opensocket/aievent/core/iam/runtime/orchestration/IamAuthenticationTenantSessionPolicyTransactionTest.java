package com.opensocket.aievent.core.iam.runtime.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opensocket.aievent.core.iam.api.application.port.IamAdministrationProjectionPort;
import com.opensocket.aievent.core.iam.api.application.port.IamOneTimeSecretDeliveryPort;
import com.opensocket.aievent.core.iam.api.application.service.IamEffectiveAccessQueryService;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.api.request.LoginRequest;
import com.opensocket.aievent.core.iam.authentication.application.command.CreateBrowserSessionCommand;
import com.opensocket.aievent.core.iam.authentication.application.port.in.MfaAuthenticationCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.in.PasswordAuthenticationCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.in.SessionCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.BrowserSessionRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.PasswordCredentialRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.RootBootstrapStateRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.SessionPolicyRepository;
import com.opensocket.aievent.core.iam.authentication.application.result.PasswordAuthenticationResult;
import com.opensocket.aievent.core.iam.authentication.domain.BrowserSession;
import com.opensocket.aievent.core.iam.authentication.domain.CredentialSubjectType;
import com.opensocket.aievent.core.iam.authentication.domain.SessionPolicy;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityCommandPort;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityQueryPort;
import com.opensocket.aievent.core.iam.organization.application.port.in.TenantCommandPort;
import com.opensocket.aievent.core.iam.persistence.dao.IamApiRuntimeDao;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.runtime.config.IamRuntimeProperties;
import com.opensocket.aievent.core.iam.runtime.credential.CanonicalCredentialBroker;
import com.opensocket.aievent.core.iam.runtime.credential.LegacyPasswordCredentialAdapter;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import com.opensocket.aievent.core.iam.token.application.port.in.AccessTokenCommandPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class IamAuthenticationTenantSessionPolicyTransactionTest {
    private static final Instant NOW = Instant.parse("2026-08-11T08:30:00Z");

    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void tenantSessionPolicyReadRunsInsideTenantTransactionDuringLoginSessionCreation() {
        PasswordAuthenticationCommandPort passwords = mock(PasswordAuthenticationCommandPort.class);
        when(passwords.authenticate(any())).thenReturn(new PasswordAuthenticationResult(
                CredentialSubjectType.HUMAN_USER,
                "user-a",
                "user.a",
                "tenant-a",
                false,
                false,
                1L,
                NOW));

        LegacyPasswordCredentialAdapter legacy = mock(LegacyPasswordCredentialAdapter.class);
        when(legacy.hasActiveLink("user.a")).thenReturn(false);
        CanonicalCredentialBroker broker = new CanonicalCredentialBroker(passwords, legacy);

        SessionPolicyRepository policies = mock(SessionPolicyRepository.class);
        SessionPolicy tenantPolicy = SessionPolicy.secureDefault();
        when(policies.findTenantPolicy("tenant-a")).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(IamTenantContextHolder.require().tenantId()).isEqualTo("tenant-a");
            assertThat(IamTenantContextHolder.require().actorId()).isEqualTo("user-a");
            return Optional.of(tenantPolicy);
        });

        SessionCommandPort sessions = mock(SessionCommandPort.class);
        when(sessions.create(any(CreateBrowserSessionCommand.class), any(SessionPolicy.class)))
                .thenAnswer(invocation -> {
                    CreateBrowserSessionCommand command = invocation.getArgument(0);
                    SessionPolicy policy = invocation.getArgument(1);
                    return BrowserSession.create(
                            "session-a",
                            command.subjectType(),
                            command.subjectId(),
                            command.tenant(),
                            command.methods(),
                            command.mfaVerifiedAt(),
                            NOW,
                            policy,
                            command.ipAddress(),
                            command.userAgent(),
                            SecurityEpoch.ZERO);
                });

        IamApiRuntimeDao dao = mock(IamApiRuntimeDao.class);
        when(dao.activeTenantChoices("user-a")).thenReturn(List.of());

        IdentityQueryPort identities = mock(IdentityQueryPort.class);
        when(identities.findHumanUser(any())).thenReturn(Optional.empty());

        IamFederationRuntimeOrchestrator federation = mock(IamFederationRuntimeOrchestrator.class);
        TransactionTemplate tx = new TransactionTemplate(new RecordingTransactionManager());

        IamAuthenticationRuntimeOrchestrator orchestrator = new IamAuthenticationRuntimeOrchestrator(
                passwords,
                mock(MfaAuthenticationCommandPort.class),
                sessions,
                mock(BrowserSessionRepository.class),
                policies,
                mock(PasswordCredentialRepository.class),
                mock(RootBootstrapStateRepository.class),
                mock(AccessTokenCommandPort.class),
                identities,
                mock(IdentityCommandPort.class),
                mock(TenantCommandPort.class),
                mock(IamAdministrationProjectionPort.class),
                dao,
                mock(IamLoginChallengeService.class),
                mock(IamOneTimeSecretDeliveryPort.class),
                mock(IamIdempotencyExecutor.class),
                tx,
                new IamRuntimeProperties(),
                broker,
                legacy,
                mock(IamEffectiveAccessQueryService.class),
                federation,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var response = orchestrator.login(
                new LoginRequest("user.a", "temporary-password", "tenant-a"),
                new IamApiRequestContext(
                        Optional.empty(),
                        "corr-a",
                        "",
                        "",
                        "127.0.0.1",
                        "test",
                        NOW,
                        java.util.Set.of()));

        assertThat(response.state()).isEqualTo("AUTHENTICATED");
        assertThat(response.session()).isNotNull();
        assertThat(response.session().tenantId()).isEqualTo("tenant-a");
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // AbstractPlatformTransactionManager publishes the transaction synchronization state.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
