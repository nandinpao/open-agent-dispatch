package com.opensocket.aievent.core.iam.runtime.machine;

import com.opensocket.aievent.core.iam.runtime.config.IamMachineTokenProperties;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientCredentialsAuthenticationToken;

/** Client Credentials grant backed by current OpenDispatch RBAC + machine-boundary authority. */
public final class OpenDispatchClientCredentialsAuthenticationProvider implements AuthenticationProvider {
    private final IamMachineTokenRuntimeOrchestrator orchestrator;
    private final IamMachineTokenProperties properties;
    public OpenDispatchClientCredentialsAuthenticationProvider(IamMachineTokenRuntimeOrchestrator orchestrator,IamMachineTokenProperties properties){this.orchestrator=orchestrator;this.properties=properties;}

    @Override public Authentication authenticate(Authentication authentication){
        if(!(authentication instanceof OAuth2ClientCredentialsAuthenticationToken grant))return null;
        if(!(grant.getPrincipal() instanceof OAuth2ClientAuthenticationToken client)||!client.isAuthenticated()||client.getRegisteredClient()==null)
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_client"));
        if(!(client.getDetails() instanceof IamMachineTokenRuntimeOrchestrator.AuthenticatedMachineClient authenticated))
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_client","OpenDispatch machine client context is missing",null));
        String resource=parameter(grant.getAdditionalParameters().get("resource"));
        if(resource.isBlank()&&properties.isAllowLegacyAudienceParameter()) resource=parameter(grant.getAdditionalParameters().get("audience"));
        try{
            var issued=orchestrator.issueAuthenticated(authenticated,grant.getScopes(),resource);
            OAuth2AccessToken accessToken=new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,issued.accessToken(),issued.issuedAt(),issued.expiresAt(),issued.scopes());
            Map<String,Object> additional=new LinkedHashMap<>();
            additional.put("resource",issued.audience());
            // Kept for one transition window so existing OpenDispatch clients can inspect the effective audience.
            additional.put("audience",issued.audience());
            additional.put("correlation_id",authenticated.correlationId());
            return new OAuth2AccessTokenAuthenticationToken(client.getRegisteredClient(),client,accessToken,null,additional);
        }catch(MachineOAuthException e){throw OpenDispatchMachineClientAuthenticationProvider.oauth(e);}
    }

    @Override public boolean supports(Class<?> authentication){return OAuth2ClientCredentialsAuthenticationToken.class.isAssignableFrom(authentication);}

    private static String parameter(Object value){
        if(value==null)return "";
        if(value instanceof String s)return s.trim();
        if(value instanceof String[] a)return a.length==1?a[0].trim():"";
        if(value instanceof Collection<?> c)return c.size()==1?String.valueOf(c.iterator().next()).trim():"";
        return String.valueOf(value).trim();
    }
}
