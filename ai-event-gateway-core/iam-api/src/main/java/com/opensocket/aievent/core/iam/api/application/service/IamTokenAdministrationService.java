package com.opensocket.aievent.core.iam.api.application.service;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.api.request.*;
import com.opensocket.aievent.core.iam.api.response.*;
import com.opensocket.aievent.core.iam.token.application.command.*;
import com.opensocket.aievent.core.iam.token.application.port.in.*;
import com.opensocket.aievent.core.iam.token.domain.TokenScope;
import com.opensocket.aievent.core.iam.rbac.application.command.BindRoleCommand;
import com.opensocket.aievent.core.iam.rbac.application.command.ChangeRoleBindingCommand;
import com.opensocket.aievent.core.iam.rbac.application.port.in.RbacAdministrationPort;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.Set;

public final class IamTokenAdministrationService {
    private static final long DEFAULT_CREDENTIAL_TTL_SECONDS = Duration.ofDays(180).toSeconds();
    private static final int DEFAULT_MAX_ACTIVE_CREDENTIALS = 2;

    private final AccessTokenCommandPort tokens;
    private final ServiceAccountCommandPort accounts;
    private final ServiceAccountCredentialCommandPort credentials;
    private final RbacAdministrationPort rbac;
    private final IamIdempotencyExecutor idem;
    private final Clock clock;

    public IamTokenAdministrationService(
            AccessTokenCommandPort tokens,
            ServiceAccountCommandPort accounts,
            ServiceAccountCredentialCommandPort credentials,
            RbacAdministrationPort rbac,
            IamIdempotencyExecutor idem,
            Clock clock) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.credentials = credentials;
        this.rbac = rbac;
        this.idem = idem;
        this.clock = clock;
    }

    private TokenScope scope(TokenScopeRequest r) {
        return new TokenScope(r.permissions(),r.audiences(),r.apiPrefixes(),r.allowedCidrs());
    }

    public ServiceAccountResponse createServiceAccount(CreateServiceAccountRequest r,IamApiRequestContext c) {
        String k=c.requireIdempotencyKey(),t=c.activeTenantId();
        long credentialTtl = r.credentialMaxTtlSeconds()==null || r.credentialMaxTtlSeconds()<=0
                ? DEFAULT_CREDENTIAL_TTL_SECONDS : r.credentialMaxTtlSeconds();
        int maxCredentials = r.maxActiveCredentials()==null || r.maxActiveCredentials()<=0
                ? DEFAULT_MAX_ACTIVE_CREDENTIALS : r.maxActiveCredentials();
        String serviceId=r.serviceAccountId()==null||r.serviceAccountId().isBlank()
                ?IamOperationIds.resourceId("svc","security.service-account.create",t,k):r.serviceAccountId();
        String bindingId=IamOperationIds.resourceId("sarb","security.service-account.responsibility",t,serviceId);
        String scopeType=scopeType(r.responsibilityScopeType());
        String scopeId=scopeId(scopeType,r.responsibilityScopeId(),r.ownerDepartmentId(),t);
        PrincipalRef principal=serviceAccountPrincipal(serviceId);
        return idem.execute(t,c.actorId(),"security.service-account.create",k,r,201,ServiceAccountResponse.class,()->{
            var account=accounts.create(new CreateServiceAccountCommand(
                    t,serviceId,r.name(),r.description(),r.ownerUserId(),r.ownerDepartmentId(),bindingId,scope(r.restrictions()),
                    r.machineScopes()==null?Set.of():r.machineScopes(),
                    r.allowedSourceSystems()==null?Set.of():r.allowedSourceSystems(),
                    Duration.ofSeconds(r.tokenMaxTtlSeconds()),r.maxActiveTokens(),
                    Duration.ofSeconds(credentialTtl),maxCredentials,r.rateLimitPerMinute(),r.nextReviewAt(),
                    c.actorId(),c.correlationId()));
            Instant now=clock.instant();
            rbac.bindRole(new BindRoleCommand(bindingId,t,principal,r.responsibilityRoleId(),scopeType,scopeId,
                    now,null,c.actorId(),"Service Account machine responsibility",now));
            return ServiceAccountResponse.from(account,r.responsibilityRoleId());
        });
    }

    public ServiceAccountResponse updateMachineBoundary(String serviceId, UpdateServiceAccountMachineBoundaryRequest r, IamApiRequestContext c) {
        String k=c.requireIdempotencyKey(),t=c.activeTenantId();
        return idem.execute(t,c.actorId(),"security.service-account.machine-boundary.update",k,r,200,ServiceAccountResponse.class,()->{
            var existing=accounts.require(t,serviceId);
            String bindingId=existing.responsibilityBindingId().isBlank()
                    ?IamOperationIds.resourceId("sarb","security.service-account.responsibility",t,serviceId)
                    :existing.responsibilityBindingId();
            String scopeType=scopeType(r.responsibilityScopeType());
            String scopeId=scopeId(scopeType,r.responsibilityScopeId(),existing.ownerDepartmentId(),t);
            PrincipalRef principal=serviceAccountPrincipal(serviceId);
            Instant now=clock.instant();
            if(existing.responsibilityBindingId().isBlank()) {
                rbac.bindRole(new BindRoleCommand(bindingId,t,principal,r.responsibilityRoleId(),scopeType,scopeId,
                        now,null,c.actorId(),"Service Account machine responsibility migrated",now));
            } else {
                rbac.changeBinding(new ChangeRoleBindingCommand(t,bindingId,principal,r.responsibilityRoleId(),scopeType,scopeId,
                        c.actorId(),"Service Account machine responsibility changed",now));
            }
            var updated=accounts.updateMachineBoundary(new UpdateServiceAccountMachineBoundaryCommand(
                    t,serviceId,bindingId,scope(r.restrictions()),
                    r.machineScopes()==null?Set.of():r.machineScopes(),
                    r.allowedSourceSystems()==null?Set.of():r.allowedSourceSystems(),
                    Duration.ofSeconds(r.credentialMaxTtlSeconds()),r.maxActiveCredentials(),r.expectedVersion(),
                    c.actorId(),c.correlationId()));
            return ServiceAccountResponse.from(updated,r.responsibilityRoleId());
        });
    }

    public IssuedServiceAccountCredentialResponse issueCredential(String serviceId, CreateServiceAccountCredentialRequest r, IamApiRequestContext c) {
        String k=c.requireIdempotencyKey(),t=c.activeTenantId();
        return idem.executeNonReplayableSecret(t,c.actorId(),"security.service-account.credential.issue",k,r,201,()->
                IssuedServiceAccountCredentialResponse.from(credentials.issue(new IssueServiceAccountCredentialCommand(
                        t,serviceId,r.name(),Duration.ofSeconds(r.ttlSeconds()),c.actorId(),c.correlationId()))));
    }

    public IssuedServiceAccountCredentialResponse rotateCredential(String serviceId, String credentialId, RotateTokenRequest r, IamApiRequestContext c) {
        String k=c.requireIdempotencyKey(),t=c.activeTenantId();
        return idem.executeNonReplayableSecret(t,c.actorId(),"security.service-account.credential.rotate",k,r,200,()->
                IssuedServiceAccountCredentialResponse.from(credentials.rotate(new RotateServiceAccountCredentialCommand(
                        t,serviceId,credentialId,Duration.ofSeconds(r.overlapSeconds()),c.actorId(),c.correlationId()))));
    }

    public void revokeCredential(String serviceId, String credentialId, RevokeTokenRequest r, IamApiRequestContext c) {
        String k=c.requireIdempotencyKey(),t=c.activeTenantId();
        idem.execute(t,c.actorId(),"security.service-account.credential.revoke",k,r,204,String.class,()->{
            credentials.revoke(new RevokeServiceAccountCredentialCommand(
                    t,serviceId,credentialId,r.reason(),c.actorId(),c.correlationId()));
            return "OK";
        });
    }

    private static PrincipalRef serviceAccountPrincipal(String serviceId) {
        return new PrincipalRef(PrincipalRef.PrincipalType.SERVICE_ACCOUNT, serviceId);
    }

    private static String scopeType(String requested) {
        return requested == null || requested.isBlank() ? "DEPARTMENT" : requested.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String scopeId(String type,String requested,String ownerDepartmentId,String tenantId) {
        return switch(type) {
            case "DEPARTMENT", "DEPARTMENT_SUBTREE" -> requested == null || requested.isBlank() ? ownerDepartmentId : requested.trim();
            case "TENANT" -> tenantId;
            case "GROUP" -> {
                if (requested == null || requested.isBlank()) throw new IllegalArgumentException("responsibilityScopeId is required for GROUP scope");
                yield requested.trim();
            }
            default -> throw new IllegalArgumentException("Machine responsibilities support TENANT, DEPARTMENT, DEPARTMENT_SUBTREE or GROUP scope");
        };
    }

    public IssuedTokenResponse issuePersonal(IssueTokenRequest r,IamApiRequestContext c){String k=c.requireIdempotencyKey(),t=c.activeTenantId();return idem.execute(t,c.actorId(),"security.token.personal.issue",k,r,201,IssuedTokenResponse.class,()->IssuedTokenResponse.from(tokens.issuePersonal(new IssuePersonalAccessTokenCommand(t,c.actorId(),r.name(),scope(r.scope()),Duration.ofSeconds(r.ttlSeconds()),c.actorId(),c.correlationId()))));}
    public IssuedTokenResponse issueService(String serviceId,IssueTokenRequest r,IamApiRequestContext c){String k=c.requireIdempotencyKey(),t=c.activeTenantId();return idem.execute(t,c.actorId(),"security.token.service.issue",k,r,201,IssuedTokenResponse.class,()->IssuedTokenResponse.from(tokens.issueService(new IssueServiceAccountTokenCommand(t,serviceId,r.name(),scope(r.scope()),Duration.ofSeconds(r.ttlSeconds()),c.actorId(),c.correlationId()))));}
    public IssuedTokenResponse rotate(String tokenId,RotateTokenRequest r,IamApiRequestContext c){String k=c.requireIdempotencyKey(),t=c.activeTenantId();return idem.execute(t,c.actorId(),"security.token.rotate",k,r,200,IssuedTokenResponse.class,()->IssuedTokenResponse.from(tokens.rotate(new RotateAccessTokenCommand(t,tokenId,Duration.ofSeconds(r.overlapSeconds()),c.actorId(),c.correlationId()))));}
    public void revoke(String tokenId,RevokeTokenRequest r,IamApiRequestContext c){String k=c.requireIdempotencyKey(),t=c.activeTenantId();idem.execute(t,c.actorId(),"security.token.revoke",k,r,204,String.class,()->{tokens.revoke(new RevokeAccessTokenCommand(t,tokenId,r.reason(),c.actorId(),c.correlationId()));return "OK";});}
}
