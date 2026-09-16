package com.opensocket.aievent.core.integration.identity;

import com.opensocket.aievent.core.issuetracking.identity.*;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Objects;

/** Selects Provider principal/credential metadata after Resource Access has authorized the Human action. */
public final class ProviderWriteIdentityService implements ProviderWriteIdentityPort {
    private final IntegrationIdentityRepository identities;
    private final ExternalActorBindingRepository bindings;

    public ProviderWriteIdentityService(IntegrationIdentityRepository identities,ExternalActorBindingRepository bindings){
        this.identities=Objects.requireNonNull(identities,"identities");this.bindings=Objects.requireNonNull(bindings,"bindings");
    }

    @Override
    public ProviderWriteIdentityResolution resolve(String tenantId,String humanPrincipalId,String mappingId,
            IntegrationOperation operation,OffsetDateTime at){
        String tenant=req(tenantId,"tenantId"), human=req(humanPrincipalId,"humanPrincipalId"), mappingKey=req(mappingId,"mappingId");
        Objects.requireNonNull(operation,"operation");OffsetDateTime now=at==null?OffsetDateTime.now():at;
        IntegrationProjectMapping mapping=identities.findMapping(tenant,mappingKey)
                .orElseThrow(()->new IllegalArgumentException("PROJECT_MAPPING_NOT_FOUND"));
        if(!mapping.enabled()||mapping.lifecycleStatus()==ProjectMappingLifecycle.DISABLED||mapping.lifecycleStatus()==ProjectMappingLifecycle.DEPRECATED)
            throw new IllegalStateException("PROJECT_MAPPING_NOT_ACTIVE");
        ProviderWriteIdentityPolicy policy=mapping.providerWriteIdentityPolicy();
        if(policy==ProviderWriteIdentityPolicy.WRITE_PROHIBITED) throw new IllegalStateException("EXTERNAL_WRITE_IDENTITY_POLICY_DENIED");
        if(policy==ProviderWriteIdentityPolicy.USER_DELEGATED_IF_VERIFIED){
            ExternalActorBindingRef binding=bindings.findVerified(tenant,human,mapping.connectionId())
                    .filter(v->v.verifiedAt(now)).orElseThrow(()->new IllegalStateException("EXTERNAL_ACTOR_BINDING_REQUIRED"));
            IntegrationPrincipal principal=activePrincipal(tenant,binding.integrationPrincipalId(),mapping.connectionId());
            if(principal.principalType()!=IntegrationPrincipalType.USER_DELEGATED) throw new IllegalStateException("EXTERNAL_ACTOR_BINDING_PRINCIPAL_TYPE_INVALID");
            IntegrationCredentialMetadata credential=activeCredential(tenant,binding.credentialId(),principal.principalId(),now);
            return new ProviderWriteIdentityResolution(policy,mapping.connectionId(),mapping.mappingId(),principal.principalId(),
                    credential.credentialId(),credential.secretVersion(),binding.providerActorId(),true);
        }
        String principalId=principalFor(mapping,operation);
        IntegrationPrincipal principal=activePrincipal(tenant,principalId,mapping.connectionId());
        if(policy==ProviderWriteIdentityPolicy.SERVICE_ACCOUNT_ON_BEHALF_OF
                && principal.principalType()==IntegrationPrincipalType.USER_DELEGATED)
            throw new IllegalStateException("SERVICE_ACCOUNT_POLICY_CANNOT_USE_USER_DELEGATED_PRINCIPAL");
        if(principal.principalType()==IntegrationPrincipalType.BREAK_GLASS)
            throw new IllegalStateException("BREAK_GLASS_PROVIDER_PRINCIPAL_FORBIDDEN");
        IntegrationCredentialMetadata credential=identities.listCredentials(tenant,principal.principalId(),200).stream()
                .filter(v->usable(v,now)).max(Comparator.comparing(IntegrationCredentialMetadata::version))
                .orElseThrow(()->new IllegalStateException("ACTIVE_PROVIDER_CREDENTIAL_REQUIRED"));
        return new ProviderWriteIdentityResolution(policy,mapping.connectionId(),mapping.mappingId(),principal.principalId(),
                credential.credentialId(),credential.secretVersion(),"",false);
    }

    private IntegrationPrincipal activePrincipal(String tenant,String principalId,String connectionId){
        IntegrationPrincipal principal=identities.findPrincipal(tenant,req(principalId,"integrationPrincipalId"))
                .orElseThrow(()->new IllegalArgumentException("INTEGRATION_PRINCIPAL_NOT_FOUND"));
        if(!connectionId.equals(principal.connectionId()))throw new IllegalStateException("INTEGRATION_PRINCIPAL_CONNECTION_MISMATCH");
        if(principal.status()!=IntegrationPrincipalStatus.ACTIVE&&principal.status()!=IntegrationPrincipalStatus.DEGRADED)
            throw new IllegalStateException("INTEGRATION_PRINCIPAL_NOT_ACTIVE");
        return principal;
    }
    private IntegrationCredentialMetadata activeCredential(String tenant,String credentialId,String principalId,OffsetDateTime at){
        IntegrationCredentialMetadata c=identities.findCredential(tenant,credentialId)
                .orElseThrow(()->new IllegalArgumentException("INTEGRATION_CREDENTIAL_NOT_FOUND"));
        if(!principalId.equals(c.principalId())||!usable(c,at))throw new IllegalStateException("INTEGRATION_CREDENTIAL_NOT_USABLE");return c;
    }
    private static boolean usable(IntegrationCredentialMetadata c,OffsetDateTime at){
        return (c.status()==IntegrationCredentialStatus.ACTIVE||c.status()==IntegrationCredentialStatus.GRACE_PERIOD)
                &&(c.validFrom()==null||!c.validFrom().isAfter(at))&&(c.expiresAt()==null||c.expiresAt().isAfter(at));
    }
    private static String principalFor(IntegrationProjectMapping m,IntegrationOperation op){return switch(op){
        case READ->m.readPrincipalId();case CREATE->m.createPrincipalId();case COMMENT->m.commentPrincipalId();
        case UPDATE->m.updatePrincipalId();case RELATION->m.relationPrincipalId();case WEBHOOK->m.webhookPrincipalId();};}
    private static String req(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required");return v.trim();}
}
