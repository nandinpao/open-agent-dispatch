package com.opensocket.aievent.core.iam.authentication.application.service;

import com.opensocket.aievent.core.iam.authentication.application.command.*;
import com.opensocket.aievent.core.iam.authentication.application.port.in.RootAuthenticationCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.*;
import com.opensocket.aievent.core.iam.authentication.application.result.*;
import com.opensocket.aievent.core.iam.authentication.domain.*;
import com.opensocket.aievent.core.iam.authentication.event.AuthenticationSecurityEvent;
import com.opensocket.aievent.core.iam.identity.application.command.ChangeRootIdentityStatusCommand;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityCommandPort;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityQueryPort;
import com.opensocket.aievent.core.iam.identity.domain.*;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import java.time.*;
import java.util.*;

public final class RootAuthenticationService implements RootAuthenticationCommandPort {
    private final RootBootstrapStateRepository bootstrap;
    private final RootRecoveryGrantRepository recovery;
    private final BrowserSessionRepository sessions;
    private final IdentityCommandPort identityCommands;
    private final IdentityQueryPort identityQueries;
    private final SecurityEpochPort epochs;
    private final OneTimeSecretPort secrets;
    private final AuthenticationEventPublisher events;
    private final Clock clock;

    public RootAuthenticationService(RootBootstrapStateRepository bootstrap, RootRecoveryGrantRepository recovery,
            BrowserSessionRepository sessions, IdentityCommandPort identityCommands, IdentityQueryPort identityQueries,
            SecurityEpochPort epochs, OneTimeSecretPort secrets, AuthenticationEventPublisher events, Clock clock) {
        this.bootstrap=Objects.requireNonNull(bootstrap);this.recovery=Objects.requireNonNull(recovery);
        this.sessions=Objects.requireNonNull(sessions);this.identityCommands=Objects.requireNonNull(identityCommands);
        this.identityQueries=Objects.requireNonNull(identityQueries);this.epochs=Objects.requireNonNull(epochs);
        this.secrets=Objects.requireNonNull(secrets);this.events=Objects.requireNonNull(events);this.clock=Objects.requireNonNull(clock);
    }
    @Override public RootBootstrapState recordStep(RootBootstrapStepCommand c){Instant at=time(c.occurredAt());RootBootstrapState current=bootstrap.find();RootBootstrapState next=switch(c.step()){case PASSWORD_CONFIGURED->current.markPasswordConfigured();case MFA_CONFIGURED->current.markMfaConfigured();case TENANT_CREATED->current.markTenantCreated();case TENANT_ADMIN_CREATED->current.markTenantAdminCreated();case COMPLETE->current.complete(at);};RootBootstrapState saved=bootstrap.save(next,c.expectedVersion());publish(c.step()==RootBootstrapStepCommand.Step.COMPLETE?"ROOT_BOOTSTRAP_COMPLETED":"ROOT_BOOTSTRAP_STEP_COMPLETED",c.actorId(),c.correlationId(),c.step().name(),at,Map.of());return saved;}
    @Override public RootRecoveryGrantResult issue(IssueRootRecoveryGrantCommand c){Instant at=time(c.occurredAt());Duration ttl=c.ttl()==null?Duration.ofMinutes(10):c.ttl();if(ttl.isNegative()||ttl.isZero()||ttl.compareTo(Duration.ofMinutes(30))>0)throw new IllegalArgumentException("Recovery grant TTL must be <= 30 minutes");String secret=secrets.generate(32);RootRecoveryGrant grant=new RootRecoveryGrant(UUID.randomUUID().toString(),c.initiatorId(),c.approverId(),secrets.hash(secret),at,at.plus(ttl),Optional.empty(),RootRecoveryGrant.Status.ACTIVE,1);recovery.save(grant,0);publish("ROOT_RECOVERY_GRANT_ISSUED",c.initiatorId(),c.correlationId(),"DUAL_CONTROL",at,Map.of("grantId",grant.grantId()));return new RootRecoveryGrantResult(grant.grantId(),secret,grant.expiresAt());}
    @Override public RootRecoverySessionResult consume(ConsumeRootRecoveryGrantCommand c){Instant at=time(c.occurredAt());RootRecoveryGrant grant=recovery.find(c.grantId()).orElseThrow(this::invalid);if(!secrets.matches(c.secret(),grant.grantHash()))throw invalid();RootRecoveryGrant consumed=grant.consume(at);recovery.save(consumed,c.expectedVersion());activateRootForRecovery(c.correlationId(),at);var epoch=epochs.increment("","root",grant.initiatorId());BrowserSession session=BrowserSession.create(secrets.generate(32),CredentialSubjectType.INSTANCE_ROOT,"root",TenantRef.instance(),Set.of("RECOVERY"),Optional.of(at),at,SessionPolicy.rootRecovery(),c.ipAddress(),c.userAgent(),epoch);sessions.save(session,0);publish("ROOT_RECOVERY_SESSION_CREATED",grant.initiatorId(),c.correlationId(),"RECOVERY_GRANT_CONSUMED",at,Map.of("grantId",grant.grantId(),"sessionId",session.sessionId()));return new RootRecoverySessionResult(grant.grantId(),session.sessionId(),session.absoluteExpiresAt());}
    @Override public void closeRecoverySession(CloseRootRecoverySessionCommand c){Instant at=time(c.occurredAt());BrowserSession session=sessions.find(c.sessionId()).orElseThrow(()->new AuthenticationDomainException(AuthenticationReasonCode.AUTH_SESSION_REVOKED,"Recovery session not found"));if(session.subjectType()!=CredentialSubjectType.INSTANCE_ROOT)throw new AuthenticationDomainException(AuthenticationReasonCode.ROOT_OPERATION_NOT_ALLOWED,"Not a root recovery session");sessions.save(session.revoke(c.actorId(),c.reason(),at),c.expectedSessionVersion());RootIdentity root=identityQueries.findRootIdentity().orElseThrow(()->new AuthenticationDomainException(AuthenticationReasonCode.ROOT_OPERATION_NOT_ALLOWED,"Root identity not found"));if(root.status()==RootIdentityStatus.ACTIVE){identityCommands.changeRootIdentityStatus(new ChangeRootIdentityStatusCommand(RootIdentityStatus.LOCKED_AFTER_RECOVERY,root.version(),"Recovery session closed: "+c.reason(),c.actorId(),c.correlationId(),UUID.randomUUID().toString()));}epochs.increment("","root",c.actorId());publish("ROOT_AUTO_LOCKED_AFTER_RECOVERY",c.actorId(),c.correlationId(),c.reason(),at,Map.of("sessionId",c.sessionId()));}
    private void activateRootForRecovery(String correlation,Instant at){RootIdentity root=identityQueries.findRootIdentity().orElseThrow(()->new AuthenticationDomainException(AuthenticationReasonCode.ROOT_OPERATION_NOT_ALLOWED,"Root identity not found"));if(root.status()==RootIdentityStatus.LOCKED_AFTER_RECOVERY){identityCommands.changeRootIdentityStatus(new ChangeRootIdentityStatusCommand(RootIdentityStatus.ACTIVE,root.version(),"Approved break-glass recovery","root-recovery",correlation,UUID.randomUUID().toString()));}else if(root.status()!=RootIdentityStatus.ACTIVE){throw new AuthenticationDomainException(AuthenticationReasonCode.ROOT_OPERATION_NOT_ALLOWED,"Root identity is not recoverable from status "+root.status());}}
    private AuthenticationDomainException invalid(){return new AuthenticationDomainException(AuthenticationReasonCode.ROOT_RECOVERY_GRANT_INVALID,"Invalid root recovery grant");}
    private void publish(String type,String actor,String correlation,String reason,Instant at,Map<String,String>metadata){events.publish(new AuthenticationSecurityEvent(UUID.randomUUID().toString(),type,CredentialSubjectType.INSTANCE_ROOT.name(),"root","",actor,correlation,reason,metadata,at));}
    private Instant time(Instant at){return at==null?clock.instant():at;}
}
