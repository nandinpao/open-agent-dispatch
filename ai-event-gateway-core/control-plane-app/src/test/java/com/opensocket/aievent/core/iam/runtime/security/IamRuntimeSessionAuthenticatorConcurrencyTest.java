package com.opensocket.aievent.core.iam.runtime.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opensocket.aievent.core.iam.authentication.application.command.TouchSessionCommand;
import com.opensocket.aievent.core.iam.authentication.application.port.in.SessionCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.BrowserSessionRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.SecurityEpochPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.SessionPolicyRepository;
import com.opensocket.aievent.core.iam.authentication.domain.BrowserSession;
import com.opensocket.aievent.core.iam.authentication.domain.CredentialSubjectType;
import com.opensocket.aievent.core.iam.authentication.domain.SessionPolicy;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import com.opensocket.aievent.core.iam.persistence.dao.IamApiRuntimeDao;
import com.opensocket.aievent.core.iam.runtime.config.IamRuntimeProperties;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import com.opensocket.aievent.core.iam.security.contract.SubjectRef;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class IamRuntimeSessionAuthenticatorConcurrencyTest {
    private static final Instant NOW = Instant.parse("2026-08-06T01:00:00Z");

    @Test
    void recentParallelRequestDoesNotWriteTheSessionAgain() {
        BrowserSessionRepository sessions = mock(BrowserSessionRepository.class);
        SessionCommandPort commands = mock(SessionCommandPort.class);
        BrowserSession recent = session(NOW.minusSeconds(5), 4);
        when(sessions.find("root-session")).thenReturn(Optional.of(recent));

        var authenticator = authenticator(sessions, commands);
        var context = authenticator.authenticate(locator());

        assertThat(context.subject().identityType()).isEqualTo(SubjectRef.IdentityType.INSTANCE_ROOT);
        verify(commands, never()).touch(any(TouchSessionCommand.class), any(SessionPolicy.class));
    }

    @Test
    void optimisticTouchConflictUsesTheSessionRefreshedByAParallelRequest() {
        BrowserSessionRepository sessions = mock(BrowserSessionRepository.class);
        SessionCommandPort commands = mock(SessionCommandPort.class);
        BrowserSession stale = session(NOW.minusSeconds(90), 4);
        BrowserSession refreshed = session(NOW.minusSeconds(1), 5);
        when(sessions.find("root-session"))
                .thenReturn(Optional.of(stale), Optional.of(stale), Optional.of(refreshed));
        when(commands.touch(any(TouchSessionCommand.class), any(SessionPolicy.class)))
                .thenThrow(new IamOptimisticLockException("BrowserSession", "root-session", 4));

        var authenticator = authenticator(sessions, commands);
        var context = authenticator.authenticate(locator());

        assertThat(context.subject().identityType()).isEqualTo(SubjectRef.IdentityType.INSTANCE_ROOT);
        verify(commands).touch(any(TouchSessionCommand.class), any(SessionPolicy.class));
    }

    private IamRuntimeSessionAuthenticator authenticator(
            BrowserSessionRepository sessions,
            SessionCommandPort commands) {
        SessionPolicyRepository policies = mock(SessionPolicyRepository.class);
        SecurityEpochPort epochs = mock(SecurityEpochPort.class);
        when(epochs.current("", "root")).thenReturn(SecurityEpoch.ZERO);
        return new IamRuntimeSessionAuthenticator(
                sessions,
                commands,
                policies,
                epochs,
                mock(IamApiRuntimeDao.class),
                immediateTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new IamRuntimeProperties());
    }

    private TransactionTemplate immediateTransactions() {
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        return transactions;
    }

    private IamSessionCookieCodec.Locator locator() {
        return new IamSessionCookieCodec.Locator(
                "INSTANCE", "", "root-session", NOW.plusSeconds(600));
    }

    private BrowserSession session(Instant lastSeenAt, long version) {
        return BrowserSession.reconstitute(
                "root-session",
                CredentialSubjectType.INSTANCE_ROOT,
                "root",
                TenantRef.instance(),
                Set.of("PASSWORD", "MFA"),
                Optional.of(NOW.minusSeconds(600)),
                NOW.minusSeconds(900),
                lastSeenAt,
                NOW.plusSeconds(300),
                NOW.plusSeconds(1_800),
                "127.0.0.1",
                "test",
                BrowserSession.Status.ACTIVE,
                Optional.empty(),
                "",
                "",
                SecurityEpoch.ZERO,
                version);
    }
}
