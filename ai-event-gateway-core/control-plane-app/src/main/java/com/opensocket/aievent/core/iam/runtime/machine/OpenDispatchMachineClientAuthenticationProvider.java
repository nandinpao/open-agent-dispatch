package com.opensocket.aievent.core.iam.runtime.machine;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/** Bridges Spring Security client authentication to the canonical OpenDispatch credential authority. */
public final class OpenDispatchMachineClientAuthenticationProvider implements AuthenticationProvider {
    private final IamMachineTokenRuntimeOrchestrator orchestrator;
    private final RegisteredClientRepository clients;
    public OpenDispatchMachineClientAuthenticationProvider(IamMachineTokenRuntimeOrchestrator orchestrator,RegisteredClientRepository clients){this.orchestrator=orchestrator;this.clients=clients;}

    @Override public Authentication authenticate(Authentication authentication){
        if(!(authentication instanceof OAuth2ClientAuthenticationToken token))return null;
        if(!ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(token.getClientAuthenticationMethod()))return null;
        String clientId=String.valueOf(token.getPrincipal());
        String secret=token.getCredentials()==null?"":String.valueOf(token.getCredentials());
        String ip=String.valueOf(token.getAdditionalParameters().getOrDefault(OpenDispatchClientSecretBasicAuthenticationConverter.SOURCE_IP,""));
        String correlation=String.valueOf(token.getAdditionalParameters().getOrDefault(OpenDispatchClientSecretBasicAuthenticationConverter.CORRELATION_ID,""));
        try{
            var authenticated=orchestrator.authenticateClient(clientId,secret,ip,correlation);
            RegisteredClient registered=clients.findByClientId(clientId);
            if(registered==null)throw MachineOAuthException.invalidClient();
            OAuth2ClientAuthenticationToken result=new OAuth2ClientAuthenticationToken(registered,ClientAuthenticationMethod.CLIENT_SECRET_BASIC,null);
            result.setDetails(authenticated);
            return result;
        }catch(MachineOAuthException e){throw oauth(e);}
    }

    @Override public boolean supports(Class<?> authentication){return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);}
    static OAuth2AuthenticationException oauth(MachineOAuthException e){return new OAuth2AuthenticationException(new OAuth2Error(e.oauthError(),e.getMessage(),null),e);}
}
