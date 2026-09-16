package com.opensocket.aievent.core.iam.runtime.machine;

import com.opensocket.aievent.core.iam.rbac.application.port.out.TenantRbacExecutionPort;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.token.application.command.ValidateServiceAccountCredentialCommand;
import com.opensocket.aievent.core.iam.token.application.port.in.ServiceAccountCredentialCommandPort;
import com.opensocket.aievent.core.iam.token.application.port.out.*;
import com.opensocket.aievent.core.iam.token.application.result.IssuedMachineAccessTokenResult;
import com.opensocket.aievent.core.iam.token.application.result.ValidatedServiceAccountCredentialResult;
import com.opensocket.aievent.core.iam.token.application.service.MachineJwtApplicationService;
import com.opensocket.aievent.core.iam.token.application.service.ServiceAccountMachineAuthenticationFactory;
import com.opensocket.aievent.core.iam.token.domain.*;
import com.opensocket.aievent.core.iam.runtime.config.IamMachineTokenProperties;
import com.opensocket.aievent.core.security.incident.RuntimeIncidentControlPolicy;
import com.opensocket.aievent.core.security.audit.SecurityAuditFailureReporter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Machine OAuth domain orchestrator.
 *
 * <p>Phase 12.1 deliberately separates OAuth protocol processing from OpenDispatch authority.
 * Spring Security owns the protocol endpoint; this class owns credential validation, Tenant/RBAC
 * authority, machine-boundary evaluation, Security Epoch and the existing OpenDispatch JWT.
 * The legacy exchange method is retained only for the opt-in compatibility endpoint.</p>
 */
public final class IamMachineTokenRuntimeOrchestrator {
    private final MachineCredentialDirectoryPort directory;
    private final MachineOAuthRateLimitPort rates;
    private final ServiceAccountCredentialSecretPort secrets;
    private final TenantRbacExecutionPort tenants;
    private final ServiceAccountCredentialCommandPort credentials;
    private final ServiceAccountRepository accounts;
    private final TokenPermissionAuthorityPort authority;
    private final TokenSecurityEpochPort epochs;
    private final MachineJwtApplicationService jwt;
    private final MachineTokenAuditPort audit;
    private final Clock clock;
    private final IamMachineTokenProperties properties;
    private final RuntimeIncidentControlPolicy incidentControls;
    private final SecurityAuditFailureReporter auditFailures;

    public IamMachineTokenRuntimeOrchestrator(MachineCredentialDirectoryPort directory,MachineOAuthRateLimitPort rates,
            ServiceAccountCredentialSecretPort secrets,TenantRbacExecutionPort tenants,
            ServiceAccountCredentialCommandPort credentials,ServiceAccountRepository accounts,
            TokenPermissionAuthorityPort authority,TokenSecurityEpochPort epochs,
            MachineJwtApplicationService jwt,MachineTokenAuditPort audit,Clock clock,IamMachineTokenProperties properties,
            RuntimeIncidentControlPolicy incidentControls){
        this(directory,rates,secrets,tenants,credentials,accounts,authority,epochs,jwt,audit,clock,properties,incidentControls,SecurityAuditFailureReporter.noop());
    }

    public IamMachineTokenRuntimeOrchestrator(MachineCredentialDirectoryPort directory,MachineOAuthRateLimitPort rates,
            ServiceAccountCredentialSecretPort secrets,TenantRbacExecutionPort tenants,
            ServiceAccountCredentialCommandPort credentials,ServiceAccountRepository accounts,
            TokenPermissionAuthorityPort authority,TokenSecurityEpochPort epochs,
            MachineJwtApplicationService jwt,MachineTokenAuditPort audit,Clock clock,IamMachineTokenProperties properties,
            RuntimeIncidentControlPolicy incidentControls,SecurityAuditFailureReporter auditFailures){
        this.directory=directory;this.rates=rates;this.secrets=secrets;this.tenants=tenants;this.credentials=credentials;
        this.accounts=accounts;this.authority=authority;this.epochs=epochs;this.jwt=jwt;this.audit=audit;this.clock=clock;this.properties=properties;
        this.incidentControls=incidentControls;this.auditFailures=auditFailures==null?SecurityAuditFailureReporter.noop():auditFailures;
    }

    /** Credential exchange used by the canonical MVC protocol fallback and the opt-in legacy endpoint. */
    public IssuedMachineAccessTokenResult exchange(String grantType,String clientId,String clientSecret,Set<String> requestedScopes,
                                                    String audience,String sourceIp,String correlationId){
        if(!"client_credentials".equals(grantType)){
            deny("MACHINE_OAUTH_UNSUPPORTED_GRANT_TYPE",null,bounded(clientId,96),null,null,requestedScopes,audience,sourceIp,correlationId,clock.instant());
            throw MachineOAuthException.unsupportedGrant();
        }
        AuthenticatedMachineClient authenticated=authenticateClient(clientId,clientSecret,sourceIp,correlationId);
        return issueAuthenticated(authenticated,requestedScopes,audience);
    }

    /**
     * Authenticates the OAuth client without creating an access token. This is called by the
     * Spring Security client-authentication provider so protocol authentication and IAM authority
     * are no longer implemented by a controller.
     */
    public AuthenticatedMachineClient authenticateClient(String clientId,String clientSecret,String sourceIp,String correlationId){
        Instant now=clock.instant();
        String cid=bounded(clientId,96), secret=boundedSecret(clientSecret), ip=sourceIp==null?"":sourceIp.trim();
        if(!rates.tryAcquire("oauth:ip:"+digest(ip),properties.getTokenEndpointRateLimitPerMinute(),now)){
            deny("MACHINE_OAUTH_RATE_LIMITED",null,cid,null,null,Set.of(),"",ip,correlationId,now);
            throw MachineOAuthException.rateLimited();
        }
        if(cid.isBlank()||secret.isBlank()){
            secrets.constantTimeReject(secret);
            deny("MACHINE_OAUTH_INVALID_CLIENT",null,cid,null,null,Set.of(),"",ip,correlationId,now);
            throw MachineOAuthException.invalidClient();
        }
        MachineCredentialDirectoryPort.Entry entry=directory.resolve(cid).orElse(null);
        if(entry==null){
            secrets.constantTimeReject(secret);
            deny("MACHINE_OAUTH_INVALID_CLIENT",null,cid,null,null,Set.of(),"",ip,correlationId,now);
            throw MachineOAuthException.invalidClient();
        }
        if(incidentControls.machineAuthenticationBlocked(entry.tenantId(),entry.serviceAccountId(),entry.credentialId())){
            deny("MACHINE_OAUTH_INCIDENT_SUSPENDED",entry.tenantId(),cid,entry.serviceAccountId(),entry.credentialId(),Set.of(),"",ip,correlationId,now);
            throw MachineOAuthException.incidentSuspended();
        }
        int incidentClientLimit=incidentControls.effectiveMachineRateLimit(entry.tenantId(),entry.serviceAccountId(),entry.credentialId(),properties.getPerClientRateLimitPerMinute());
        if(!rates.tryAcquire("oauth:client:"+digest(cid),incidentClientLimit,now)){
            deny("MACHINE_OAUTH_RATE_LIMITED",entry.tenantId(),cid,entry.serviceAccountId(),entry.credentialId(),Set.of(),"",ip,correlationId,now);
            throw MachineOAuthException.rateLimited();
        }
        try {
            return tenants.write(entry.tenantId(),"oauth-client:"+entry.serviceAccountId(),()->authenticateInTenant(entry,secret,ip,correlationId));
        } catch (MachineOAuthException e){
            deny(e.reasonCode(),entry.tenantId(),cid,entry.serviceAccountId(),entry.credentialId(),Set.of(),"",ip,correlationId,clock.instant());
            throw e;
        } catch (TokenDomainException|IllegalArgumentException e){
            deny("MACHINE_OAUTH_INVALID_CLIENT",entry.tenantId(),cid,entry.serviceAccountId(),entry.credentialId(),Set.of(),"",ip,correlationId,clock.instant());
            throw MachineOAuthException.invalidClient();
        } catch (RuntimeException e){
            deny("MACHINE_OAUTH_SERVER_ERROR",entry.tenantId(),cid,entry.serviceAccountId(),entry.credentialId(),Set.of(),"",ip,correlationId,clock.instant());
            throw MachineOAuthException.serverError();
        }
    }

    /**
     * Issues a token for an already authenticated client. Current Service Account state, RBAC,
     * machine boundary and Security Epoch are re-evaluated at issuance time.
     */
    public IssuedMachineAccessTokenResult issueAuthenticated(AuthenticatedMachineClient authenticated,
                                                              Set<String> requestedScopes,String requestedResource){
        Objects.requireNonNull(authenticated,"authenticated");
        try {
            return tenants.write(authenticated.tenantId(),"oauth-client:"+authenticated.serviceAccountId(),
                    ()->issueInTenant(authenticated,requestedScopes,requestedResource));
        } catch (MachineOAuthException e){
            deny(e.reasonCode(),authenticated.tenantId(),authenticated.clientId(),authenticated.serviceAccountId(),authenticated.credentialId(),
                    requestedScopes,requestedResource,authenticated.sourceIp(),authenticated.correlationId(),clock.instant());
            throw e;
        } catch (TokenDomainException|IllegalArgumentException e){
            MachineOAuthException mapped = mapDomain(e);
            deny(mapped.reasonCode(),authenticated.tenantId(),authenticated.clientId(),authenticated.serviceAccountId(),authenticated.credentialId(),
                    requestedScopes,requestedResource,authenticated.sourceIp(),authenticated.correlationId(),clock.instant());
            throw mapped;
        } catch (RuntimeException e){
            deny("MACHINE_OAUTH_SERVER_ERROR",authenticated.tenantId(),authenticated.clientId(),authenticated.serviceAccountId(),authenticated.credentialId(),
                    requestedScopes,requestedResource,authenticated.sourceIp(),authenticated.correlationId(),clock.instant());
            throw MachineOAuthException.serverError();
        }
    }

    private AuthenticatedMachineClient authenticateInTenant(MachineCredentialDirectoryPort.Entry entry,String secret,String sourceIp,String correlationId){
        var validated=credentials.validate(new ValidateServiceAccountCredentialCommand(entry.tenantId(),entry.clientId(),secret,correlationId));
        ServiceAccount account=accounts.find(entry.tenantId(),new ServiceAccountId(validated.serviceAccountId()))
                .orElseThrow(()->new TokenDomainException(TokenReasonCode.SERVICE_ACCOUNT_NOT_FOUND,"Service account not found"));
        PrincipalRef principal=new PrincipalRef(PrincipalRef.PrincipalType.SERVICE_ACCOUNT,account.serviceAccountId().value());
        authority.requireActivePrincipal(account.tenantId(),principal);
        if(!account.restrictions().permitsIp(sourceIp)) throw new TokenDomainException(TokenReasonCode.AUTH_TOKEN_CIDR_DENIED,"CIDR denied");
        if(authority.effectivePermissions(account.tenantId(),principal).isEmpty())
            throw new TokenDomainException(TokenReasonCode.AUTH_TOKEN_SCOPE_INSUFFICIENT,"No effective Service Account authority");
        return new AuthenticatedMachineClient(validated.tenantId(),validated.serviceAccountId(),validated.credentialId(),validated.clientId(),
                validated.issuedAt(),validated.expiresAt(),sourceIp==null?"":sourceIp,correlationId==null?"":correlationId);
    }

    private IssuedMachineAccessTokenResult issueInTenant(AuthenticatedMachineClient authenticated,Set<String> requestedScopes,String requestedResource){
        Instant now=clock.instant();
        ServiceAccount account=accounts.find(authenticated.tenantId(),new ServiceAccountId(authenticated.serviceAccountId()))
                .orElseThrow(()->new TokenDomainException(TokenReasonCode.SERVICE_ACCOUNT_NOT_FOUND,"Service account not found"));
        PrincipalRef principal=new PrincipalRef(PrincipalRef.PrincipalType.SERVICE_ACCOUNT,account.serviceAccountId().value());
        authority.requireActivePrincipal(account.tenantId(),principal);
        if(!account.restrictions().permitsIp(authenticated.sourceIp()))
            throw new TokenDomainException(TokenReasonCode.AUTH_TOKEN_CIDR_DENIED,"CIDR denied");
        Set<String> effective=authority.effectivePermissions(account.tenantId(),principal);
        if(effective.isEmpty()) throw new TokenDomainException(TokenReasonCode.AUTH_TOKEN_SCOPE_INSUFFICIENT,"No effective Service Account authority");
        var epoch=epochs.current(account.tenantId(),principal);
        var validated=new ValidatedServiceAccountCredentialResult(authenticated.credentialId(),authenticated.serviceAccountId(),authenticated.clientId(),
                authenticated.tenantId(),authenticated.credentialIssuedAt(),authenticated.credentialExpiresAt());
        var context=ServiceAccountMachineAuthenticationFactory.fromValidatedCredential(account,validated,effective,epoch,now,now.plus(properties.getAccessTokenTtl()));
        IssuedMachineAccessTokenResult issued;
        try { issued=jwt.issue(context,authenticated.clientId(),requestedScopes,requestedResource); }
        catch(MachineJwtApplicationService.MachineTokenProtocolException e){
            if("invalid_scope".equals(e.oauthError())) throw MachineOAuthException.invalidScope();
            if("invalid_target".equals(e.oauthError())) throw MachineOAuthException.invalidTarget();
            if("invalid_client".equals(e.oauthError())) throw MachineOAuthException.invalidClient();
            throw MachineOAuthException.serverError();
        }
        audit.append(new MachineTokenAuditPort.Event(UUID.randomUUID().toString(),"ALLOW","MACHINE_ACCESS_TOKEN_ISSUED",
                account.tenantId(),account.serviceAccountId().value(),authenticated.credentialId(),authenticated.clientId(),issued.jwtId(),
                issued.scopes(),issued.audience(),authenticated.sourceIp(),authenticated.correlationId(),now));
        return issued;
    }

    private static MachineOAuthException mapDomain(RuntimeException e){
        if(e instanceof TokenDomainException t){
            return switch(t.reasonCode()){
                case AUTH_TOKEN_SCOPE_INSUFFICIENT -> MachineOAuthException.invalidScope();
                case AUTH_TOKEN_AUDIENCE_DENIED -> MachineOAuthException.invalidTarget();
                default -> MachineOAuthException.invalidClient();
            };
        }
        return MachineOAuthException.invalidClient();
    }

    private void deny(String reason,String tenant,String client,String account,String credential,Set<String> scopes,String audience,String ip,String correlation,Instant at){
        try{audit.append(new MachineTokenAuditPort.Event(UUID.randomUUID().toString(),"DENY",reason,tenant,account,credential,blank(client),"",scopes==null?Set.of():Set.copyOf(scopes),blank(audience),blank(ip),correlation,at));}
        catch(RuntimeException failure){auditFailures.report("MACHINE_OAUTH","MACHINE_TOKEN",tenant,account,correlation,reason,failure);}
    }
    private static String bounded(String v,int max){if(v==null)return "";String x=v.trim();return x.length()<=max?x:"";}
    private static String boundedSecret(String v){if(v==null||v.isBlank()||v.length()>512)return "";return v;}
    private static String blank(String v){return v==null?"":v;}
    private static String digest(String v){try{byte[] h=MessageDigest.getInstance("SHA-256").digest((v==null?"":v).getBytes(StandardCharsets.UTF_8));return Base64.getUrlEncoder().withoutPadding().encodeToString(h);}catch(Exception e){throw new IllegalStateException(e);}}

    public record AuthenticatedMachineClient(
            String tenantId,String serviceAccountId,String credentialId,String clientId,
            Instant credentialIssuedAt,Instant credentialExpiresAt,String sourceIp,String correlationId) { }
}
