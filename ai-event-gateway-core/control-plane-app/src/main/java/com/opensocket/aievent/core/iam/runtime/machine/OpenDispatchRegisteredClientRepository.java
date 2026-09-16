package com.opensocket.aievent.core.iam.runtime.machine;

import com.opensocket.aievent.core.iam.rbac.application.port.out.TenantRbacExecutionPort;
import com.opensocket.aievent.core.iam.runtime.config.IamMachineTokenProperties;
import com.opensocket.aievent.core.iam.token.application.port.out.MachineCredentialDirectoryPort;
import com.opensocket.aievent.core.iam.token.application.port.out.ServiceAccountRepository;
import com.opensocket.aievent.core.iam.token.domain.ServiceAccountId;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * Read-only Spring Authorization Server view of OpenDispatch machine credentials.
 * RegisteredClient is a protocol projection; ServiceAccount remains the canonical business principal.
 */
public final class OpenDispatchRegisteredClientRepository implements RegisteredClientRepository {
    private final MachineCredentialDirectoryPort directory;
    private final TenantRbacExecutionPort tenants;
    private final ServiceAccountRepository accounts;
    private final IamMachineTokenProperties properties;

    public OpenDispatchRegisteredClientRepository(MachineCredentialDirectoryPort directory,TenantRbacExecutionPort tenants,
            ServiceAccountRepository accounts,IamMachineTokenProperties properties){
        this.directory=directory;this.tenants=tenants;this.accounts=accounts;this.properties=properties;
    }

    @Override public void save(RegisteredClient registeredClient){
        throw new UnsupportedOperationException("OAuth clients are governed by OpenDispatch Service Account credentials, not RegisteredClientRepository.save");
    }

    @Override public RegisteredClient findById(String id){return findByClientId(id);}

    @Override public RegisteredClient findByClientId(String clientId){
        if(clientId==null||clientId.isBlank())return null;
        var entry=directory.resolve(clientId.trim()).orElse(null);
        if(entry==null)return null;
        return tenants.write(entry.tenantId(),"oauth-registered-client:"+entry.serviceAccountId(),()->
                accounts.find(entry.tenantId(),new ServiceAccountId(entry.serviceAccountId())).map(account->{
                    RegisteredClient.Builder builder=RegisteredClient.withId(entry.clientId())
                            .clientId(entry.clientId())
                            .clientName(account.name())
                            // This sentinel is never verified. OpenDispatchMachineClientAuthenticationProvider
                            // validates the clear secret against the canonical credential store first.
                            .clientSecret("{noop}opendispatch-managed-credential")
                            .clientSecretExpiresAt(entry.expiresAt())
                            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                            .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(properties.getAccessTokenTtl()).build());
                    account.machineScopes().forEach(builder::scope);
                    return builder.build();
                }).orElse(null));
    }
}
